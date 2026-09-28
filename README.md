# Banking Transaction Processor Service

A concurrent, high-integrity RESTful banking transaction processor built
with **Java 17**, **Spring Boot 3.2.5**, and **Spring Data JPA**.

The service handles account lifecycle management, credit/debit
operations, and inter-account transfers. It enforces zero-overdraft
invariants, prevents transfer deadlocks through deterministic lock
ordering, maintains an immutable append-only transaction ledger, and
follows clean separation of responsibilities.

------------------------------------------------------------------------

## 1. System Architecture & Concurrency Strategy

``` text
                          ┌───────────────────────────┐
                          │     BankingController     │
                          │   (REST & OpenAPI Docs)   │
                          └─────────────┬─────────────┘
                                        │
                                        ▼
                          ┌───────────────────────────┐
                          │      BankingService       │
                          │   (@Transactional ACID)   │
                          └─────────────┬─────────────┘
                                        │
                 ┌──────────────────────┴──────────────────────┐
                 ▼                                             ▼
  ┌─────────────────────────────┐               ┌─────────────────────────────┐
  │      AccountRepository      │               │    TransactionRepository    │
  │   (PESSIMISTIC_WRITE Lock)  │               │   (Append-Only Audit Log)   │
  └──────────────┬──────────────┘               └──────────────┬──────────────┘
                 │                                             │
                 └──────────────────────┬──────────────────────┘
                                        │
                                        ▼
                          ┌───────────────────────────┐
                          │    H2 In-Memory RDBMS     │
                          │    (MVCC / Row Locking)   │
                          └───────────────────────────┘
```

### Concurrency & Deadlock Avoidance

#### The Problem

Concurrent transfers can create a deadlock when two transactions attempt
to transfer funds in opposite directions.

For example:

``` text
Thread 1: A → B
Thread 2: B → A
```

If Thread 1 locks Account A and waits for Account B while Thread 2 locks
Account B and waits for Account A, a circular wait can occur.

#### The Solution

The service uses **deterministic lexicographical lock ordering**.

Account IDs are ordered before acquiring row locks:

``` java
String firstId =
        sourceId.compareTo(destinationId) < 0
                ? sourceId
                : destinationId;

String secondId =
        sourceId.compareTo(destinationId) < 0
                ? destinationId
                : sourceId;

Account firstLocked =
        accountRepository.findByIdForUpdate(firstId)
                .orElseThrow(...);

Account secondLocked =
        accountRepository.findByIdForUpdate(secondId)
                .orElseThrow(...);
```

Regardless of transfer direction, concurrent transactions request the
locks in the same deterministic order. This removes the circular-wait
condition that causes this class of transfer deadlock.

### Isolation & Locking Mechanics

The service uses:

``` java
@Lock(LockModeType.PESSIMISTIC_WRITE)
```

to serialize concurrent write mutations on the relevant account rows.

Conceptually, this corresponds to database row locking such as:

``` sql
SELECT ...
FROM account
WHERE account_id = ?
FOR UPDATE;
```

Optimistic locking using `@Version` was not selected for the
debit/transfer path because high-contention account transactions can
produce optimistic-lock conflicts and retries.

### Financial Precision

All monetary values use:

``` java
java.math.BigDecimal
```

rather than `double` or `float`.

This avoids binary floating-point representation issues when handling
monetary values.

Where rounding is required, the service uses:

``` java
RoundingMode.HALF_EVEN
```

### Immutable Ledger

Transaction records are treated as immutable ledger entries.

The ledger is append-only. A successful transfer creates two linked
entries:

``` text
TRANSFER_OUT
TRANSFER_IN
```

Both entries carry correlation information so the two sides of the
transfer can be associated for audit and reconciliation.

------------------------------------------------------------------------

## 2. API Endpoints Specification

All endpoints are versioned under:

``` text
/api/v1/accounts
```

Endpoint paths are centralized in `ApiEndpoints.java`.

  ----------------------------------------------------------------------------------------------------
  Method            Endpoint                                      Description       Status
  ----------------- --------------------------------------------- ----------------- ------------------
  **POST**          `/api/v1/accounts`                            Open account with `201 Created`
                                                                  initial balance   

  **GET**           `/api/v1/accounts/{accountId}/balance`        Query current     `200 OK`
                                                                  cleared balance   

  **GET**           `/api/v1/accounts/{accountId}/transactions`   Query audit       `200 OK`
                                                                  ledger history    

  **POST**          `/api/v1/accounts/{accountId}/deposits`       Credit funds to   `200 OK`
                                                                  target account    

  **POST**          `/api/v1/accounts/{accountId}/withdrawals`    Debit funds with  `200 OK`
                                                                  zero-overdraft    
                                                                  validation        

  **POST**          `/api/v1/accounts/transfers`                  Atomic transfer   `204 No Content`
                                                                  between distinct  
                                                                  accounts          
  ----------------------------------------------------------------------------------------------------

### Error Model

The service uses standardized `ProblemDetail` responses.

#### `400 Bad Request`

Used for invalid requests such as:

-   Invalid input
-   Non-positive transaction amounts
-   Invalid account data
-   Self-transfer attempts

#### `404 Not Found`

Returned when the requested account does not exist.

#### `422 Unprocessable Entity`

Returned when an operation cannot be completed because the account has
insufficient funds.

Example:

``` json
{
  "type": "about:blank",
  "title": "Insufficient Funds",
  "status": 422,
  "detail": "Insufficient funds for account ACC-001"
}
```

------------------------------------------------------------------------

## 3. Local Development, Swagger & Database Console

### Prerequisites

-   Java 17+
-   Maven 3.8+
-   Git

The project includes the Maven Wrapper, so Maven does not need to be
installed globally.

### OpenAPI / Swagger

Interactive Swagger UI:

``` text
http://localhost:8080/swagger-ui.html
```

Raw OpenAPI specification:

``` text
http://localhost:8080/v3/api-docs
```

### H2 Database Console

Console:

``` text
http://localhost:8080/h2-console
```

Connection settings:

``` text
Driver Class:
org.h2.Driver

JDBC URL:
jdbc:h2:mem:bankingdb

Username:
sa

Password:
<leave blank>
```

The JDBC URL must match the datasource configuration in
`application.yml`.

------------------------------------------------------------------------

## 4. Build, Verification & Testing

The project is designed around automated testing and includes unit,
integration, concurrency, and Cucumber BDD coverage.

The tests cover:

-   Account creation
-   Deposits
-   Withdrawals
-   Transfers
-   Insufficient funds
-   Self-transfer rejection
-   Account-not-found scenarios
-   Transaction history
-   Balance invariants
-   Concurrent transaction behaviour
-   REST API behaviour
-   Cucumber BDD scenarios

### Build and Run All Tests

Linux/macOS:

``` bash
./mvnw clean verify
```

Windows:

``` cmd
mvnw.cmd clean verify
```

### Run Cucumber BDD Tests

Linux/macOS:

``` bash
./mvnw test -Dtest=CucumberTestRunner
```

Windows:

``` cmd
mvnw.cmd test -Dtest=CucumberTestRunner
```

### Verify Spotless Formatting

``` bash
./mvnw spotless:check
```

### Apply Spotless Formatting

``` bash
./mvnw spotless:apply
```

### Start the Application

Linux/macOS:

``` bash
./mvnw spring-boot:run
```

Windows:

``` cmd
mvnw.cmd spring-boot:run
```

------------------------------------------------------------------------

## 5. BDD Scenarios

The project includes Cucumber BDD scenarios that describe important
business behaviour.

### Successful Deposit

``` gherkin
Scenario: Successfully deposit funds into an account
    Given an account exists with ID "ACC-BDD-01" and an initial balance of 100.00
    When a deposit of 50.00 with reference "Salary credit" is made to "ACC-BDD-01"
    Then the cleared balance of "ACC-BDD-01" should be 150.00
```

### Prevent Overdraft

``` gherkin
Scenario: Prevent overdraft during account withdrawal
    Given an account exists with ID "ACC-BDD-02" and an initial balance of 30.00
    When attempting a withdrawal of 50.00 from "ACC-BDD-02"
    Then the operation should fail due to insufficient funds
    And the cleared balance of "ACC-BDD-02" should remain 30.00
```

### Atomic Transfer

``` gherkin
Scenario: Automatically transfer funds between two distinct accounts
    Given an account exists with ID "ACC-SRC" and an initial balance of 200.00
    And an account exists with ID "ACC-DST" and an initial balance of 50.00
    When a transfer of 75.00 is executed from "ACC-SRC" to "ACC-DST" with reference "Transfer"
    Then the cleared balance of "ACC-SRC" should be 125.00
    And the cleared balance of "ACC-DST" should be 125.00
```

### Self-Transfer Rejection

``` gherkin
Scenario: Reject self-transfers
    Given an account exists with ID "ACC-SELF" and an initial balance of 100.00
    When a transfer of 20.00 is attempted from "ACC-SELF" to "ACC-SELF"
    Then the operation should fail due to an invalid self-transfer attempt
```

The BDD scenarios act as executable documentation for the business
rules.

------------------------------------------------------------------------

## 6. Important Business Invariants

### Positive Transaction Amounts

Transaction amounts must be greater than zero.

``` text
amount > 0
```

Invalid examples:

``` text
-100.00
0.00
```

### No Overdraft

Before a withdrawal or transfer debit:

``` text
currentBalance >= requestedAmount
```

If the condition is not satisfied:

-   The operation fails.
-   The account balance remains unchanged.
-   No successful transaction ledger entry is created.

### No Self-Transfer

The source and destination accounts must be different:

``` text
sourceAccountId != destinationAccountId
```

### Atomic Transfer

A transfer is treated as a single database transaction:

``` text
Debit source
+
Credit destination
+
TRANSFER_OUT ledger entry
+
TRANSFER_IN ledger entry
```

Either the complete operation succeeds or the transaction is rolled
back.

### Ledger Consistency

Successful mutations create corresponding ledger entries:

``` text
Deposit
   ↓
DEPOSIT

Withdrawal
   ↓
WITHDRAWAL

Transfer
   ↓
TRANSFER_OUT
TRANSFER_IN
```

------------------------------------------------------------------------

## 7. Architectural Trade-Offs

The following decisions were made deliberately to keep the
implementation simple and focused while satisfying the coding exercise
requirements.

### 7.1 Embedded H2 vs Dedicated RDBMS

**Chosen:** H2 in-memory database.

#### Why?

-   Zero external infrastructure
-   Simple local setup
-   Fast test execution
-   Easy evaluation
-   Suitable for the coding exercise

#### Production Evolution

For production, PostgreSQL or Oracle could be used depending on
organizational standards.

Potential production improvements:

-   Partitioning of high-volume transaction history
-   Read replicas where appropriate
-   Backup and recovery strategy
-   Database monitoring
-   Connection pool tuning

### 7.2 Synchronous REST vs Event-Driven Messaging

**Chosen:** Synchronous REST APIs with transactional service operations.

The core banking operations require immediate consistency.

For example:

``` text
Transfer
   ↓
Lock source + destination
   ↓
Validate balance
   ↓
Debit source
   ↓
Credit destination
   ↓
Create ledger entries
   ↓
Commit
```

### Production Evolution

For downstream asynchronous consumers, a **Transactional Outbox**
pattern could be introduced:

``` text
Banking Transaction
        │
        ▼
Database Transaction
   ┌───────────────┐
   │ Account       │
   │ Ledger        │
   │ Outbox Event  │
   └───────────────┘
          │
          ▼
      Outbox Relay
          │
          ▼
        Kafka
```

This would allow analytics, notifications, and other downstream
consumers without compromising the atomic account transaction.

### 7.3 Account Balance vs Double-Entry General Ledger

**Chosen:** Maintain the current account balance directly on the
`Account` entity and maintain transaction history separately as an
append-only ledger.

This makes balance queries efficient without recalculating the balance
from the complete transaction history for every request.

#### Production Evolution

A full banking platform could evolve toward a double-entry General
Ledger with appropriate accounting controls, reconciliation, and
chart-of-accounts support.

------------------------------------------------------------------------

## 8. Future Improvements

The following improvements are intentionally outside the core timeboxed
implementation.

### Idempotency

Mutation endpoints could support:

``` http
Idempotency-Key: <unique-request-id>
```

This would help prevent duplicate processing when clients retry requests
after network failures or timeouts.

### Multi-Currency

The monetary model could be extended to represent:

``` text
amount + currency
```

using ISO-4217 currency codes.

For example:

``` text
100.00 GBP
100.00 USD
```

An FX component could then be introduced for currency conversion where
required.

### Distributed Coordination

For horizontally scaled deployments, distributed coordination mechanisms
such as Redis/Redisson could be evaluated where database-level locking
is insufficient for a particular workload.

### Resilience

For public-facing APIs and external dependencies, resilience patterns
could be introduced where appropriate:

-   Timeouts
-   Rate limiting
-   Circuit breakers
-   Bulkheads
-   Retry policies

Resilience4j could be evaluated for external service dependencies.

### Observability

A production implementation could additionally include:

-   Structured logging
-   Metrics
-   Distributed tracing
-   Correlation IDs
-   Transaction audit monitoring
-   Operational dashboards

------------------------------------------------------------------------

## 9. Project Structure

``` text
src
├── main
│   ├── java
│   │   └── com.banking.processor
│   │       ├── config
│   │       │   └── SwaggerConfig.java
│   │       ├── constant
│   │       │   └── ApiEndpoints.java
│   │       ├── controller
│   │       │   └── BankingController.java
│   │       ├── domain
│   │       │   ├── Account.java
│   │       │   ├── Transaction.java
│   │       │   └── TransactionType.java
│   │       ├── dto
│   │       ├── exception
│   │       ├── repository
│   │       │   ├── AccountRepository.java
│   │       │   └── TransactionRepository.java
│   │       └── service
│   │           └── BankingService.java
│   └── resources
│       └── application.yml
└── test
    ├── java
    │   └── com.banking.processor
    │       ├── bdd
    │       │   ├── BankingStepDefinitions.java
    │       │   └── CucumberTestRunner.java
    │       └── repository
    │           ├── AccountRepositoryTest.java
    │           └── TransactionRepositoryTest.java
    └── resources
        └── features
            └── banking_operations.feature
```

------------------------------------------------------------------------

## 10. Design Principles

The implementation focuses on:

-   **Single Responsibility Principle**
-   **Clear separation of concerns**
-   **Domain-oriented design**
-   **Immutable transaction records**
-   **Explicit validation**
-   **Transactional integrity**
-   **Deterministic locking**
-   **Testable business logic**
-   **Readable code**
-   **Meaningful domain-level exceptions**

The goal is to keep the implementation simple while making the important
banking invariants explicit and testable.

------------------------------------------------------------------------

## 11. What I Would Improve With More Time

Given the timeboxed nature of the coding exercise, the implementation
prioritizes the core requirements first.

With additional development time, I would consider:

1.  Additional concurrency stress tests.
2.  More comprehensive API contract tests.
3.  Testcontainers-based PostgreSQL integration tests.
4.  Idempotency support for mutation APIs.
5.  Transaction correlation and reconciliation reporting.
6.  Authentication and authorization.
7.  Observability with structured logging and metrics.
8.  Production database migration tooling such as Flyway.
9.  Performance benchmarking under concurrent transaction load.
10. Pagination for very large transaction histories.

------------------------------------------------------------------------

## 12. Development Notes & Deliberate Decisions

The coding task explicitly values understanding before implementation,
evidence of iteration, ownership of trade-offs, clean code, edge-case
consideration, object orientation, test-driven development, and
meaningful Git history.

The implementation therefore prioritizes:

1.  Correctness of financial operations.
2.  Explicit business invariants.
3.  Atomicity of transfers.
4.  Safe concurrent access to account balances.
5.  An auditable transaction history.
6.  Automated tests as executable documentation.
7.  Simple design over unnecessary infrastructure.

Where a production-scale concern is intentionally not implemented, it is
documented under **Architectural Trade-Offs** or **Future Improvements**
rather than being hidden.

------------------------------------------------------------------------

## 13. Conclusion

The Banking Transaction Processor is designed around a small set of
explicit guarantees:

``` text
                 ┌──────────────────────┐
                 │ Valid Transaction    │
                 └──────────┬───────────┘
                            │
                            ▼
                 ┌──────────────────────┐
                 │ Validate Input       │
                 └──────────┬───────────┘
                            │
                            ▼
                 ┌──────────────────────┐
                 │ Acquire DB Locks     │
                 │ Deterministically    │
                 └──────────┬───────────┘
                            │
                            ▼
                 ┌──────────────────────┐
                 │ Validate Balance     │
                 └──────────┬───────────┘
                            │
                            ▼
                 ┌──────────────────────┐
                 │ Update Account(s)    │
                 └──────────┬───────────┘
                            │
                            ▼
                 ┌──────────────────────┐
                 │ Append Ledger Entry  │
                 └──────────┬───────────┘
                            │
                            ▼
                 ┌──────────────────────┐
                 │ Atomic Commit        │
                 └──────────────────────┘
```

The implementation prioritizes correctness, transactional integrity,
deterministic concurrency behaviour, auditability, testability, and
readable code while keeping the scope appropriate for the coding
exercise.
