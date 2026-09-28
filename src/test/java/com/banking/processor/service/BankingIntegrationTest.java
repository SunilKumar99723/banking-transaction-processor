package com.banking.processor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.banking.processor.dto.CreateAccountRequest;
import com.banking.processor.dto.DepositRequest;
import com.banking.processor.dto.TransactionResponse;
import com.banking.processor.dto.TransferRequest;
import com.banking.processor.dto.WithdrawRequest;
import com.banking.processor.exception.InsufficientFundsException;
import com.banking.processor.exception.SelfTransferException;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@DisplayName("Banking Service End-to-End & Concurrency Specifications")
class BankingIntegrationTest {

    @Autowired private BankingService bankingService;

    @Test
    @DisplayName("Deposit, withdrawal, and audit ledger integrity")
    void shouldProcessOperationsAndMaintainLedger() {
        String accountId = "ACC-001";
        bankingService.createAccount(new CreateAccountRequest(accountId, new BigDecimal("100.00")));

        bankingService.deposit(
                accountId, new DepositRequest(new BigDecimal("50.00"), "Salary bonus"));
        bankingService.withdraw(
                accountId, new WithdrawRequest(new BigDecimal("30.00"), "Groceries"));

        BigDecimal balance = bankingService.getBalance(accountId).balance();
        assertThat(balance).isEqualByComparingTo(new BigDecimal("120.00"));

        List<TransactionResponse> history = bankingService.getTransactionHistory(accountId);
        assertThat(history).hasSize(3);
        assertThat(history.get(0).amount()).isEqualByComparingTo(new BigDecimal("30.00"));
        assertThat(history.get(0).resultingBalance())
                .isEqualByComparingTo(new BigDecimal("120.00"));
    }

    @Test
    @DisplayName("Transfer between two accounts should execute atomically")
    void shouldExecuteAtomicTransfer() {
        String sender = "ACC-A";
        String receiver = "ACC-B";

        bankingService.createAccount(new CreateAccountRequest(sender, new BigDecimal("200.00")));
        bankingService.createAccount(new CreateAccountRequest(receiver, new BigDecimal("50.00")));

        bankingService.transfer(
                new TransferRequest(sender, receiver, new BigDecimal("75.00"), "Bill share"));

        assertThat(bankingService.getBalance(sender).balance())
                .isEqualByComparingTo(new BigDecimal("125.00"));
        assertThat(bankingService.getBalance(receiver).balance())
                .isEqualByComparingTo(new BigDecimal("125.00"));
    }

    @Test
    @DisplayName("Self-transfers are explicitly rejected")
    void shouldRejectSelfTransfer() {
        String accountId = "ACC-SELF";
        bankingService.createAccount(new CreateAccountRequest(accountId, new BigDecimal("100.00")));

        assertThatThrownBy(
                        () ->
                                bankingService.transfer(
                                        new TransferRequest(
                                                accountId,
                                                accountId,
                                                new BigDecimal("10.00"),
                                                "Self")))
                .isInstanceOf(SelfTransferException.class);
    }

    @Test
    @DisplayName(
            "Transfer aborts atomically without balance alterations when funds are insufficient")
    void shouldAbortTransferWhenInsufficientFunds() {
        String sender = "ACC-BROKE";
        String receiver = "ACC-RICH";

        bankingService.createAccount(new CreateAccountRequest(sender, new BigDecimal("20.00")));
        bankingService.createAccount(new CreateAccountRequest(receiver, new BigDecimal("500.00")));

        assertThatThrownBy(
                        () ->
                                bankingService.transfer(
                                        new TransferRequest(
                                                sender,
                                                receiver,
                                                new BigDecimal("50.00"),
                                                "Overdraft transfer")))
                .isInstanceOf(InsufficientFundsException.class);

        assertThat(bankingService.getBalance(sender).balance())
                .isEqualByComparingTo(new BigDecimal("20.00"));
        assertThat(bankingService.getBalance(receiver).balance())
                .isEqualByComparingTo(new BigDecimal("500.00"));
    }

    @Test
    @DisplayName("Concurrent opposite transfers must not deadlock and preserve total balance")
    void shouldHandleConcurrentCrossTransfersSafely() throws InterruptedException {
        String acc1 = "ACC-X";
        String acc2 = "ACC-Y";

        bankingService.createAccount(new CreateAccountRequest(acc1, new BigDecimal("1000.00")));
        bankingService.createAccount(new CreateAccountRequest(acc2, new BigDecimal("1000.00")));

        int threads = 20;
        int transfersPerThread = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            final boolean even = (i % 2 == 0);
            executor.submit(
                    () -> {
                        try {
                            startLatch.await();
                            for (int j = 0; j < transfersPerThread; j++) {
                                if (even) {
                                    bankingService.transfer(
                                            new TransferRequest(
                                                    acc1, acc2, new BigDecimal("5.00"), "X to Y"));
                                } else {
                                    bankingService.transfer(
                                            new TransferRequest(
                                                    acc2, acc1, new BigDecimal("5.00"), "Y to X"));
                                }
                            }
                        } catch (Exception ignored) {
                        } finally {
                            endLatch.countDown();
                        }
                    });
        }

        startLatch.countDown();
        boolean completed = endLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();

        BigDecimal balance1 = bankingService.getBalance(acc1).balance();
        BigDecimal balance2 = bankingService.getBalance(acc2).balance();

        assertThat(balance1.add(balance2)).isEqualByComparingTo(new BigDecimal("2000.00"));
    }

    @Test
    @DisplayName("Concurrent withdrawals must never cause race condition overdrafts")
    void shouldPreventConcurrentWithdrawalOverdraft() throws InterruptedException {
        String accountId = "ACC-RACE";
        bankingService.createAccount(new CreateAccountRequest(accountId, new BigDecimal("100.00")));

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(threadCount);

        AtomicInteger successfulWithdrawals = new AtomicInteger(0);
        AtomicInteger failedWithdrawals = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(
                    () -> {
                        try {
                            startGate.await();
                            bankingService.withdraw(
                                    accountId,
                                    new WithdrawRequest(new BigDecimal("20.00"), "Race withdraw"));
                            successfulWithdrawals.incrementAndGet();
                        } catch (InsufficientFundsException ex) {
                            failedWithdrawals.incrementAndGet();
                        } catch (Exception ignored) {
                        } finally {
                            doneGate.countDown();
                        }
                    });
        }

        startGate.countDown();
        doneGate.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(successfulWithdrawals.get()).isEqualTo(5);
        assertThat(failedWithdrawals.get()).isEqualTo(5);
        assertThat(bankingService.getBalance(accountId).balance())
                .isEqualByComparingTo(BigDecimal.ZERO);
    }
}
