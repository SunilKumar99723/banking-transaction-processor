Banking Transaction Processor Service
A concurrent, high-integrity RESTful banking transaction processor built with Java 17, Spring Boot 3.2.5, and Spring Data JPA.

The service handles account lifecycle management, high-volume credit/debit operations, and inter-account transfers. It enforces zero-overdraft invariants, eliminates transfer deadlocks through deterministic lock ordering, tracks an immutable append-only ledger, and adheres to Clean Architecture practices.

1. System Architecture & Concurrency Strategy
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
Concurrency & Deadlock Avoidance
The Problem: In concurrent transfers, Thread 1 moving funds from Account A to Account B while Thread 2 transfers from Account B to Account A causes mutual lock waits (cyclic dependency), resulting in SQL deadlocks (DeadlockLoserDataAccessException).
The Solution: The service implements lexicographical lock ordering. Account IDs are ordered deterministically before requesting row locks:
String firstId = sourceId.compareTo(destinationId) < 0 ? sourceId : destinationId;
String secondId = sourceId.compareTo(destinationId) < 0 ? destinationId : sourceId;

Account firstLocked = accountRepository.findByIdForUpdate(firstId)...;
Account secondLocked = accountRepository.findByIdForUpdate(secondId)...;
Regardless of execution direction, concurrent transactions always acquire locks in identical sequences, mathematically eliminating cyclic wait states.
Isolation & Locking Mechanics
Uses @Lock(LockModeType.PESSIMISTIC_WRITE) (SELECT ... FOR UPDATE) to serialize write mutations at the database row level.
Optimistic locking (@Version) was rejected for debit pathways because high-frequency account transactions cause high retry rates and aborted operations under contention.
Financial Precision & Immutability
All monetary values use java.math.BigDecimal with HALF_EVEN rounding to eliminate floating-point representation bugs.
All ledger transactions are immutable records. Transfers persist two linked entries (TRANSFER_OUT and TRANSFER_IN) sharing correlation metadata for audit reconciliation.
2. API Endpoints Specification
All endpoints are versioned under /api/v1/accounts and centralized in ApiEndpoints.java.

Method	Endpoint	Description	Status
POST	/api/v1/accounts	Open account with initial balance	201 Created
GET	/api/v1/accounts/{accountId}/balance	Query current cleared balance	200 OK
GET	/api/v1/accounts/{accountId}/transactions	Audit ledger history (descending)	200 OK
POST	/api/v1/accounts/{accountId}/deposits	Credit funds to target account	200 OK
POST	/api/v1/accounts/{accountId}/withdrawals	Debit funds (zero-overdraft check)	200 OK
POST	/api/v1/accounts/transfers	Atomic transfer between distinct accounts	204 No Content
Error Model (RFC 7807)
Errors return standardized ProblemDetail payloads:

400 Bad Request: Input validation failures, non-positive amounts, or circular self-transfers.
404 Not Found: Account does not exist.
422 Unprocessable Entity: Insufficient funds / overdraft attempts.
3. Local Development, Swagger & Database Consoles
OpenAPI 3 / Swagger Documentation
Interactive UI: http://localhost:8080/swagger-ui.html
Raw OpenAPI Contract (JSON): http://localhost:8080/v3/api-docs
H2 Database Web Console
Console URL: http://localhost:8080/h2-console
Saved Settings: Generic H2 (Embedded)
Driver Class: org.h2.Driver
JDBC URL: jdbc:h2:mem:bankingdb (Must match application.yml)
User Name: sa | Password: (leave blank)
4. Build, Verification & Testing
Prerequisites
Java Development Kit (JDK): Version 17 or higher
Maven: 3.8+ (or use the included ./mvnw)
Commands
# Build and execute all test suites (Unit, JPA slices, Concurrency, and Cucumber BDD)
./mvnw clean verify

# Execute Cucumber BDD test suite independently
./mvnw test -Dtest=CucumberTestRunner

# Verify Spotless code formatting
./mvnw spotless:check

# Apply Spotless formatting fixes
./mvnw spotless:apply

# Start application locally
./mvnw spring-boot:run
5. Architectural Trade-Offs (Timebox Scope)
Embedded H2 vs. Dedicated RDBMS (PostgreSQL/Oracle):
Chosen: Embedded in-memory mode enables zero-dependency compilation and immediate evaluation.
Production: PostgreSQL or Oracle with table partitioning on timestamp and dedicated read-replicas.
Synchronous REST vs. Event-Driven Messaging (Kafka):
Chosen: Synchronous atomic transactions satisfy immediate consistency requirements.
Production: Transactional Outbox pattern publishing events to Apache Kafka for downstream analytics.
Account Balances vs. Double-Entry General Ledger (GL):
Chosen: Denormalized balance on Account updated synchronously with ledger entries.
Production: Complete chart of accounts (Assets, Liabilities, Equity) with balance calculation checkpoints.
6. Future Improvements
Distributed Locks (Redisson / Redis): For horizontal scaling across multiple instances where DB row locking creates thread pool bottlenecks.
Idempotency Keys: Enforce an Idempotency-Key HTTP header on mutation endpoints backed by Redis to prevent duplicate submissions on client retries.
Multi-Currency & FX Engine: Expand the Money value object to enforce ISO-4217 currency pairing and integrate with real-time FX rates.
Resilience: Introduce Resilience4j circuit breakers and rate limiters on public-facing transfer routes.
