# billing-service

Part of [`card-billing-modernization`](https://github.com/leon-lourenco/card-billing-modernization) —
the modernized counterpart to [`card-billing-legacy`](https://github.com/leon-lourenco/card-billing-legacy).
Full architecture, contracts, and cross-cutting decisions live in that repo's
[`ARCHITECTURE.md`](https://github.com/leon-lourenco/card-billing-modernization/blob/master/ARCHITECTURE.md) —
this README covers what's specific to this one service.

Owns the invoice lifecycle: closing a billing cycle into an invoice, applying interest, and
recording payments. Every other service in this initiative calls into this one; it calls no one.

## Domain

`Customer`, `Account`, `Card`, `CardTransaction`, `Invoice`, `Payment` — the same shape as
[`card-billing-legacy`](https://github.com/leon-lourenco/card-billing-legacy)'s shared `domain`
module, migrated here since this is where that data now lives permanently. `Invoice` is the
aggregate root — `Payment` and interest application only ever happen through it.

## API

Every endpoint requires a Bearer token (Keycloak client-credentials) — this service is an
OAuth2 resource server.

| Endpoint | Purpose | Idempotency |
|---|---|---|
| `GET /invoices/overdue?asOf={date}` | Every invoice not yet paid, past due date | n/a (read) |
| `GET /invoices/search?documentNumber=&amountCents=&aroundDate=&toleranceDays=3` | Indexed lookup by customer document + amount + date window. `amountCents` is optional — omitting it drops the amount filter, for telling "owes something else in this window" apart from "owes nothing" | n/a (read) |
| `POST /invoices/{id}/interest` `{feeCents, dailyInterestCents, accrualDate}` | Applies interest/late fee | Unique per `(invoiceId, accrualDate)` — a second call for the same day is a no-op |
| `POST /invoices/{id}/payments` `{amountCents, source, externalReference, paidAt}` | Records a payment, marks the invoice paid if fully covered | Unique on `externalReference` |
| `POST /cycles/close?date=` | Closes every active card's billing cycle whose cycle day matches the date | n/a — a transaction is only ever assigned to one invoice |

Errors are `application/problem+json` (Spring's built-in RFC 7807 support) via this service's own
domain exceptions — `InvoiceNotFoundException`, `DuplicatePaymentException`,
`InterestAlreadyAccruedException`.

## Why a search endpoint, not a data dump

`reconciliation-service` needs to find an invoice matching a customer document, an amount, and a
date within a few days of the due date — the exact kind of fuzzy, non-indexed lookup that became
a nested loop in the legacy, because there was no shared ID between an external bank statement
and this system's own invoices. `GET /invoices/search` answers that question with a real
database index instead, so the nested loop doesn't need to exist at all on the modern side.

## Verified against the real platform

Not just unit-tested in isolation — run against `collections-service` and `reconciliation-service`
for real, over real HTTP, with real data: 150 seeded customers, 149 invoices closed from actual
transaction history, R$5,263.35 in interest genuinely applied via `collections-service`'s
`/collections/run`, and 3 payments recorded through `reconciliation-service`'s statement matching
(`/invoices/{id}/payments`, `source: EXTERNAL_RECONCILIATION`). See
[`collections-service`](https://github.com/leon-lourenco/collections-service#resilience--the-evidence)'s
README for the resilience run captured by killing this service mid-integration-test.

## Engineering practices

Hexagonal package structure (`domain` / `application` / `infrastructure`), enforced by ArchUnit
in every test run, not just claimed — see `ARCHITECTURE.md` in the hub repo for the exact rules
and the full reasoning. Tests written alongside implementation, not after.

## Stack

| Category | Technology | Version |
|---|---|---|
| Language | Java | 21 |
| Framework | Spring Boot | 4.1.0 |
| Build | Gradle (Kotlin DSL) | 9.7.1 |
| API docs | springdoc-openapi-starter-webmvc-ui | 3.1.0 |
| Auth | Keycloak (client-credentials, resource server) | 26.7 |
| Database | PostgreSQL | 16 |
| Architecture tests | ArchUnit | — |

## Running it

```bash
docker compose up -d          # Postgres on 5434, Keycloak on 8180
./gradlew bootRun
```

Seeds 150 synthetic customers with four months of transaction history on first run — same
dataset shape as `card-billing-legacy`. Swagger UI at `http://localhost:8081/swagger-ui.html`
once running.

The default configuration requires a Bearer token on every endpoint, so `bootRun` on its own
expects Keycloak to be up. To poke at the API with curl without that, run under the `local`
profile, which relaxes the resource-server chain to anonymous access:

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

That profile is development-only and never active unless asked for by name — see `SecurityConfig`
for exactly what it changes. The Keycloak realm in `docker/keycloak/` is the canonical four-client
export, copied into the hub repo's shared platform compose; a `card-billing-shared` repo holding
it (and the version catalog) once, shared by all four services as a git submodule, is the next
planned step — see the hub repo's `INTEGRATION.md`.

```bash
./gradlew test
```
