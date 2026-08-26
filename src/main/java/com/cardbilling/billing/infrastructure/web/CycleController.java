package com.cardbilling.billing.infrastructure.web;

import com.cardbilling.billing.application.CloseInvoiceCycleUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/cycles")
@Tag(name = "Billing cycles", description = "Closing a cycle's transactions into invoices")
class CycleController {

    private final CloseInvoiceCycleUseCase closeInvoiceCycle;

    CycleController(CloseInvoiceCycleUseCase closeInvoiceCycle) {
        this.closeInvoiceCycle = closeInvoiceCycle;
    }

    /**
     * Safe to call twice for the same date: a transaction can only ever be claimed by one
     * invoice, so a rerun finds nothing left to bill and closes nothing.
     */
    @PostMapping("/close")
    @Operation(summary = "Close the billing cycle for every active card whose cycle day is this date")
    CycleClosingResponse close(
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return CycleClosingResponse.from(closeInvoiceCycle.closeCyclesFor(date));
    }
}
