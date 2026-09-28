package com.banking.processor.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record DepositRequest(
        @NotNull(message = "Amount must not be null")
                @DecimalMin(value = "0.01", message = "Deposit amount must be at least 0.01")
                BigDecimal amount,
        String reference) {}
