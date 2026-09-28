package com.banking.processor.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record TransferRequest(
        @NotBlank(message = "Source account ID must not be blank") String sourceAccountId,
        @NotBlank(message = "Destination account ID must not be blank") String destinationAccountId,
        @NotNull(message = "Amount must not be null")
                @DecimalMin(value = "0.01", message = "Transfer amount must be at least 0.01")
                BigDecimal amount,
        String reference) {}
