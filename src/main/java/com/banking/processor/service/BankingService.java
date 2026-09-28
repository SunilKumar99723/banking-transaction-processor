package com.banking.processor.service;

import com.banking.processor.dto.AccountResponse;
import com.banking.processor.dto.BalanceResponse;
import com.banking.processor.dto.CreateAccountRequest;
import com.banking.processor.dto.DepositRequest;
import com.banking.processor.dto.TransactionResponse;
import com.banking.processor.dto.TransferRequest;
import com.banking.processor.dto.WithdrawRequest;
import java.util.List;

public interface BankingService {
    AccountResponse createAccount(CreateAccountRequest request);

    BalanceResponse getBalance(String accountId);

    List<TransactionResponse> getTransactionHistory(String accountId);

    TransactionResponse deposit(String accountId, DepositRequest request);

    TransactionResponse withdraw(String accountId, WithdrawRequest request);

    void transfer(TransferRequest request);
}
