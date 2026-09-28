package com.banking.processor.dto;

import java.math.BigDecimal;

public record BalanceResponse(String accountId, BigDecimal balance) {}
