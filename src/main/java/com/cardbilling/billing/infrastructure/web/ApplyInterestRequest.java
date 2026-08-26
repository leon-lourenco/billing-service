package com.cardbilling.billing.infrastructure.web;

import com.cardbilling.billing.application.ApplyInterestCommand;
import com.cardbilling.billing.domain.Money;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDate;

@Schema(description = """
        A request to charge one day's interest. Leave feeCents and dailyInterestCents out to have \
        this service compute them from its own interest policy; supply them to apply amounts the \
        caller has already computed.""")
public record ApplyInterestRequest(
        @PositiveOrZero(message = "feeCents must not be negative")
        Long feeCents,

        @PositiveOrZero(message = "dailyInterestCents must not be negative")
        Long dailyInterestCents,

        @NotNull(message = "accrualDate is required")
        LocalDate accrualDate) {

    ApplyInterestCommand toCommand(long invoiceId) {
        return new ApplyInterestCommand(
                invoiceId,
                feeCents == null ? null : Money.ofCents(feeCents),
                dailyInterestCents == null ? null : Money.ofCents(dailyInterestCents),
                accrualDate);
    }
}
