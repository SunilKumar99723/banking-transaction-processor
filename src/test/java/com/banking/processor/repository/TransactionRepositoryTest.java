package com.banking.processor.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.banking.processor.domain.Transaction;
import com.banking.processor.domain.TransactionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

@DataJpaTest
@DisplayName("TransactionRepository Ledger History Specifications")
class TransactionRepositoryTest {

    @Autowired private TransactionRepository transactionRepository;

    @Autowired private TestEntityManager entityManager;

    @Test
    @DisplayName(
            "Should retrieve ledger transactions sorted strictly in descending order of timestamp")
    void shouldReturnTransactionsOrderedByTimestampDesc() {
        String accountId = "ACC-TX-01";
        Instant baseTime = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        Transaction tx1 =
                Transaction.builder()
                        .accountId(accountId)
                        .type(TransactionType.DEPOSIT)
                        .amount(new BigDecimal("5000.00"))
                        .resultingBalance(new BigDecimal("5000.00"))
                        .timestamp(baseTime.minusSeconds(120))
                        .reference("Initial Deposit")
                        .build();

        Transaction tx2 =
                Transaction.builder()
                        .accountId(accountId)
                        .type(TransactionType.WITHDRAWAL)
                        .amount(new BigDecimal("1000.00"))
                        .resultingBalance(new BigDecimal("4000.00"))
                        .timestamp(baseTime.minusSeconds(60))
                        .reference("ATM Cash")
                        .build();

        Transaction tx3 =
                Transaction.builder()
                        .accountId(accountId)
                        .type(TransactionType.TRANSFER_IN)
                        .amount(new BigDecimal("2000.00"))
                        .resultingBalance(new BigDecimal("6000.00"))
                        .timestamp(baseTime)
                        .reference("Transfer from Counterparty")
                        .build();

        transactionRepository.saveAll(List.of(tx1, tx2, tx3));

        entityManager.flush();
        entityManager.clear();

        List<Transaction> history =
                transactionRepository.findByAccountIdOrderByTimestampDesc(accountId);

        assertThat(history).hasSize(3);
        // Verify chronological descending order (newest first)
        assertThat(history.get(0).getReference()).isEqualTo("Transfer from Counterparty");
        assertThat(history.get(1).getReference()).isEqualTo("ATM Cash");
        assertThat(history.get(2).getReference()).isEqualTo("Initial Deposit");
    }

    @Test
    @DisplayName("Should return empty list for accounts with no ledger entries")
    void shouldReturnEmptyListWhenNoTransactionsExist() {
        List<Transaction> history =
                transactionRepository.findByAccountIdOrderByTimestampDesc("ACC-EMPTY");
        assertThat(history).isEmpty();
    }
}
