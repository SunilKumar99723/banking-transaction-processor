package com.banking.processor.dto;

import java.math.BigDecimal;

public record AccountResponse(String accountId, BigDecimal balance) {}
