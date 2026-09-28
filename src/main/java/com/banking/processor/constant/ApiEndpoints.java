package com.banking.processor.constant;

public final class ApiEndpoints {

    private ApiEndpoints() {
        // Prevent instantiation
    }

    public static final String BASE_ACCOUNTS = "/api/v1/accounts";
    public static final String ACCOUNT_ID_PATH_VAR = "/{accountId}";
    public static final String BALANCE = "/{accountId}/balance";
    public static final String TRANSACTIONS = "/{accountId}/transactions";
    public static final String DEPOSITS = "/{accountId}/deposits";
    public static final String WITHDRAWALS = "/{accountId}/withdrawals";
    public static final String TRANSFERS = "/transfers";
}
