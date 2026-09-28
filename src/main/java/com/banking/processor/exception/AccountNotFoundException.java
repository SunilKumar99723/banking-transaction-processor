package com.banking.processor.exception;

public class AccountNotFoundException extends RuntimeException {
    public AccountNotFoundException(String accountId) {
        super("Account not found with ID: " + accountId);
    }
}
