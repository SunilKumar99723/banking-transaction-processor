package com.banking.processor.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record CreateAccountRequest(
        @NotBlank(message = "Account ID must not be blank") String accountId,
        @NotNull(message = "Initial balance must not be null")
                @DecimalMin(
                        value = "0.00",
                        inclusive = true,
                        message = "Initial balance cannot be negative")
                BigDecimal initialBalance) {}
