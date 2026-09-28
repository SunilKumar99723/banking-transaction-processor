package com.banking.processor.service;

import com.banking.processor.domain.Account;
import com.banking.processor.domain.Transaction;
import com.banking.processor.domain.TransactionType;
import com.banking.processor.dto.AccountResponse;
import com.banking.processor.dto.BalanceResponse;
import com.banking.processor.dto.CreateAccountRequest;
import com.banking.processor.dto.DepositRequest;
import com.banking.processor.dto.TransactionResponse;
import com.banking.processor.dto.TransferRequest;
import com.banking.processor.dto.WithdrawRequest;
import com.banking.processor.exception.AccountNotFoundException;
import com.banking.processor.exception.SelfTransferException;
import com.banking.processor.repository.AccountRepository;
import com.banking.processor.repository.TransactionRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BankingServiceImpl implements BankingService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    @Override
    @Transactional
    public AccountResponse createAccount(CreateAccountRequest request) {
        log.info("Creating account with ID: {}", request.accountId());
        if (accountRepository.existsById(request.accountId())) {
            throw new IllegalArgumentException(
                    "Account already exists with ID: " + request.accountId());
        }

        Account account = new Account(request.accountId(), request.initialBalance());
        Account saved = accountRepository.save(account);

        if (request.initialBalance().signum() > 0) {
            transactionRepository.save(
                    Transaction.builder()
                            .accountId(saved.getId())
                            .type(TransactionType.DEPOSIT)
                            .amount(request.initialBalance())
                            .resultingBalance(saved.getBalance())
                            .timestamp(Instant.now())
                            .reference("Initial Account Opening Deposit")
                            .build());
        }

        return new AccountResponse(saved.getId(), saved.getBalance());
    }

    @Override
    @Transactional(readOnly = true)
    public BalanceResponse getBalance(String accountId) {
        Account account =
                accountRepository
                        .findById(accountId)
                        .orElseThrow(() -> new AccountNotFoundException(accountId));
        return new BalanceResponse(account.getId(), account.getBalance());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TransactionResponse> getTransactionHistory(String accountId) {
        if (!accountRepository.existsById(accountId)) {
            throw new AccountNotFoundException(accountId);
        }
        return transactionRepository.findByAccountIdOrderByTimestampDesc(accountId).stream()
                .map(TransactionResponse::fromDomain)
                .toList();
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TransactionResponse deposit(String accountId, DepositRequest request) {
        log.info("Processing deposit of {} for account: {}", request.amount(), accountId);
        Account account =
                accountRepository
                        .findByIdForUpdate(accountId)
                        .orElseThrow(() -> new AccountNotFoundException(accountId));

        account.credit(request.amount());
        accountRepository.save(account);

        Transaction tx =
                Transaction.builder()
                        .accountId(account.getId())
                        .type(TransactionType.DEPOSIT)
                        .amount(request.amount())
                        .resultingBalance(account.getBalance())
                        .timestamp(Instant.now())
                        .reference(request.reference())
                        .build();

        Transaction savedTx = transactionRepository.save(tx);
        return TransactionResponse.fromDomain(savedTx);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TransactionResponse withdraw(String accountId, WithdrawRequest request) {
        log.info("Processing withdrawal of {} for account: {}", request.amount(), accountId);
        Account account =
                accountRepository
                        .findByIdForUpdate(accountId)
                        .orElseThrow(() -> new AccountNotFoundException(accountId));

        account.debit(request.amount());
        accountRepository.save(account);

        Transaction tx =
                Transaction.builder()
                        .accountId(account.getId())
                        .type(TransactionType.WITHDRAWAL)
                        .amount(request.amount())
                        .resultingBalance(account.getBalance())
                        .timestamp(Instant.now())
                        .reference(request.reference())
                        .build();

        Transaction savedTx = transactionRepository.save(tx);
        return TransactionResponse.fromDomain(savedTx);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void transfer(TransferRequest request) {
        String sourceId = request.sourceAccountId();
        String destinationId = request.destinationAccountId();

        if (sourceId.equals(destinationId)) {
            throw new SelfTransferException("Source and destination accounts must be distinct");
        }

        // Ordered locking to eliminate cyclic deadlocks under concurrency
        String firstId = sourceId.compareTo(destinationId) < 0 ? sourceId : destinationId;
        String secondId = sourceId.compareTo(destinationId) < 0 ? destinationId : sourceId;

        log.debug("Acquiring locks sequentially: {} then {}", firstId, secondId);
        Account firstLocked =
                accountRepository
                        .findByIdForUpdate(firstId)
                        .orElseThrow(() -> new AccountNotFoundException(firstId));
        Account secondLocked =
                accountRepository
                        .findByIdForUpdate(secondId)
                        .orElseThrow(() -> new AccountNotFoundException(secondId));

        Account sourceAccount = sourceId.equals(firstLocked.getId()) ? firstLocked : secondLocked;
        Account destinationAccount =
                destinationId.equals(firstLocked.getId()) ? firstLocked : secondLocked;

        sourceAccount.debit(request.amount());
        destinationAccount.credit(request.amount());

        accountRepository.save(sourceAccount);
        accountRepository.save(destinationAccount);

        Instant txTime = Instant.now();
        String correlationId = UUID.randomUUID().toString().substring(0, 8);
        String baseReference = request.reference() == null ? "" : " - " + request.reference();

        Transaction debitTx =
                Transaction.builder()
                        .accountId(sourceAccount.getId())
                        .type(TransactionType.TRANSFER_OUT)
                        .amount(request.amount())
                        .resultingBalance(sourceAccount.getBalance())
                        .timestamp(txTime)
                        .reference(
                                "Transfer to "
                                        + destinationAccount.getId()
                                        + " [Ref:"
                                        + correlationId
                                        + "]"
                                        + baseReference)
                        .build();

        Transaction creditTx =
                Transaction.builder()
                        .accountId(destinationAccount.getId())
                        .type(TransactionType.TRANSFER_IN)
                        .amount(request.amount())
                        .resultingBalance(destinationAccount.getBalance())
                        .timestamp(txTime)
                        .reference(
                                "Transfer from "
                                        + sourceAccount.getId()
                                        + " [Ref:"
                                        + correlationId
                                        + "]"
                                        + baseReference)
                        .build();

        transactionRepository.save(debitTx);
        transactionRepository.save(creditTx);
        log.info(
                "Completed transfer of {} from {} to {}",
                request.amount(),
                sourceId,
                destinationId);
    }
}
