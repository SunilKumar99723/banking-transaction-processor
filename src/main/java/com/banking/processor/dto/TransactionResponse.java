package com.banking.processor.dto;

import com.banking.processor.domain.Transaction;
import com.banking.processor.domain.TransactionType;
import java.math.BigDecimal;
import java.time.Instant;

public record TransactionResponse(
        Long transactionId,
        String accountId,
        TransactionType type,
        BigDecimal amount,
        BigDecimal resultingBalance,
        Instant timestamp,
        String reference) {
    public static TransactionResponse fromDomain(Transaction tx) {
        return new TransactionResponse(
                tx.getId(),
                tx.getAccountId(),
                tx.getType(),
                tx.getAmount(),
                tx.getResultingBalance(),
                tx.getTimestamp(),
                tx.getReference());
    }
}
