package com.cardbilling.billing.infrastructure.web;

import com.cardbilling.billing.application.ApplyInterestUseCase;
import com.cardbilling.billing.application.GetOverdueInvoicesUseCase;
import com.cardbilling.billing.application.InterestApplicationResult;
import com.cardbilling.billing.application.InvoiceSearchQuery;
import com.cardbilling.billing.application.PaymentRecordingResult;
import com.cardbilling.billing.application.RecordPaymentUseCase;
import com.cardbilling.billing.application.SearchInvoicesUseCase;
import com.cardbilling.billing.domain.DocumentNumber;
import com.cardbilling.billing.domain.Money;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/invoices")
@Tag(name = "Invoices", description = "The invoice lifecycle: what is owed, what interest has been charged, what has been paid")
class InvoiceController {

    private final GetOverdueInvoicesUseCase getOverdueInvoices;
    private final SearchInvoicesUseCase searchInvoices;
    private final ApplyInterestUseCase applyInterest;
    private final RecordPaymentUseCase recordPayment;

    InvoiceController(GetOverdueInvoicesUseCase getOverdueInvoices, SearchInvoicesUseCase searchInvoices,
            ApplyInterestUseCase applyInterest, RecordPaymentUseCase recordPayment) {
        this.getOverdueInvoices = getOverdueInvoices;
        this.searchInvoices = searchInvoices;
        this.applyInterest = applyInterest;
        this.recordPayment = recordPayment;
    }

    @GetMapping("/overdue")
    @Operation(summary = "Every unpaid invoice past its due date as of the given date")
    List<InvoiceResponse> overdue(
            @RequestParam("asOf") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return getOverdueInvoices.overdueAsOf(asOf).stream()
                .map(InvoiceResponse::from)
                .toList();
    }

    @GetMapping("/search")
    @Operation(summary = "Find an unpaid invoice by cardholder document, amount owed and a due-date window",
            description = """
                    The indexed lookup that replaces reconciliation's nested loop. Amount is matched \
                    against what the invoice currently owes, interest included. Omitting amountCents \
                    drops the amount filter, for telling "owes something else in this window" apart \
                    from "owes nothing in this window at all".""")
    List<InvoiceResponse> search(
            @RequestParam("documentNumber") String documentNumber,
            @RequestParam(name = "amountCents", required = false) Long amountCents,
            @RequestParam("aroundDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate aroundDate,
            @RequestParam(name = "toleranceDays", defaultValue = "3") int toleranceDays) {
        InvoiceSearchQuery query = new InvoiceSearchQuery(DocumentNumber.of(documentNumber),
                amountCents == null ? null : Money.ofCents(amountCents), aroundDate, toleranceDays);
        return searchInvoices.search(query).stream()
                .map(InvoiceResponse::from)
                .toList();
    }

    /**
     * Answers 200 whether or not anything changed: a repeat request for a day already charged is
     * a replay of a run that partly failed, not an error, and the body says which it was.
     */
    @PostMapping("/{id}/interest")
    @Operation(summary = "Charge one day's interest, at most once per invoice per day")
    ResponseEntity<InterestApplicationResponse> applyInterest(
            @PathVariable("id") long invoiceId,
            @Valid @RequestBody ApplyInterestRequest request) {
        InterestApplicationResult result = applyInterest.apply(request.toCommand(invoiceId));
        return ResponseEntity.ok(InterestApplicationResponse.from(result));
    }

    /**
     * 201 when the payment was recorded, 200 when this statement line had already been paid -
     * both are successful outcomes for a caller replaying a reconciliation run.
     */
    @PostMapping("/{id}/payments")
    @Operation(summary = "Record a payment, marking the invoice paid once it is fully covered")
    ResponseEntity<PaymentRecordingResponse> recordPayment(
            @PathVariable("id") long invoiceId,
            @Valid @RequestBody RecordPaymentRequest request) {
        PaymentRecordingResult result = recordPayment.record(request.toCommand(invoiceId));
        return ResponseEntity.status(result.recorded() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(PaymentRecordingResponse.from(result));
    }
}
