package com.banking.processor.controller;

import com.banking.processor.constant.ApiEndpoints;
import com.banking.processor.dto.AccountResponse;
import com.banking.processor.dto.BalanceResponse;
import com.banking.processor.dto.CreateAccountRequest;
import com.banking.processor.dto.DepositRequest;
import com.banking.processor.dto.TransactionResponse;
import com.banking.processor.dto.TransferRequest;
import com.banking.processor.dto.WithdrawRequest;
import com.banking.processor.service.BankingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = ApiEndpoints.BASE_ACCOUNTS, produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(
        name = "Banking Operations",
        description = "Operations for accounts, ledgers, deposits, withdrawals, and transfers")
public class BankingController {

    private final BankingService bankingService;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Create an account",
            description =
                    "Creates a new account with a unique identifier and non-negative initial"
                            + " balance.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "201",
                description = "Account created successfully",
                content = @Content(schema = @Schema(implementation = AccountResponse.class))),
        @ApiResponse(
                responseCode = "400",
                description = "Invalid payload or account ID already exists",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<AccountResponse> createAccount(
            @Valid @RequestBody CreateAccountRequest request) {
        AccountResponse response = bankingService.createAccount(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping(ApiEndpoints.BALANCE)
    @Operation(
            summary = "Query account balance",
            description = "Retrieves the current cleared balance for an account.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Balance retrieved successfully",
                content = @Content(schema = @Schema(implementation = BalanceResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "Account not found",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<BalanceResponse> getBalance(
            @Parameter(description = "Account ID", example = "ACC-ID-01", required = true)
                    @PathVariable
                    String accountId) {
        return ResponseEntity.ok(bankingService.getBalance(accountId));
    }

    @GetMapping(ApiEndpoints.TRANSACTIONS)
    @Operation(
            summary = "Query transaction ledger",
            description =
                    "Retrieves the chronological audit ledger of transactions for an account,"
                            + " sorted newest first.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Ledger history retrieved successfully",
                content =
                        @Content(
                                array =
                                        @ArraySchema(
                                                schema =
                                                        @Schema(
                                                                implementation =
                                                                        TransactionResponse
                                                                                .class)))),
        @ApiResponse(
                responseCode = "404",
                description = "Account not found",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<List<TransactionResponse>> getTransactions(
            @Parameter(description = "Account ID", example = "ACC-ID-01", required = true)
                    @PathVariable
                    String accountId) {
        return ResponseEntity.ok(bankingService.getTransactionHistory(accountId));
    }

    @PostMapping(value = ApiEndpoints.DEPOSITS, consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Deposit funds",
            description =
                    "Credits funds to the specified account and adds a DEPOSIT record to the"
                            + " ledger.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Deposit processed successfully",
                content = @Content(schema = @Schema(implementation = TransactionResponse.class))),
        @ApiResponse(
                responseCode = "400",
                description = "Invalid deposit amount",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "404",
                description = "Account not found",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<TransactionResponse> deposit(
            @Parameter(description = "Target account ID", example = "ACC-ID-01", required = true)
                    @PathVariable
                    String accountId,
            @Valid @RequestBody DepositRequest request) {
        return ResponseEntity.ok(bankingService.deposit(accountId, request));
    }

    @PostMapping(value = ApiEndpoints.WITHDRAWALS, consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Withdraw funds",
            description =
                    "Debits funds if sufficient balance is available and adds a WITHDRAWAL record"
                            + " to the ledger.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Withdrawal processed successfully",
                content = @Content(schema = @Schema(implementation = TransactionResponse.class))),
        @ApiResponse(
                responseCode = "400",
                description = "Invalid withdrawal amount",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "404",
                description = "Account not found",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "422",
                description = "Insufficient funds",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<TransactionResponse> withdraw(
            @Parameter(description = "Source account ID", example = "ACC-ID-01", required = true)
                    @PathVariable
                    String accountId,
            @Valid @RequestBody WithdrawRequest request) {
        return ResponseEntity.ok(bankingService.withdraw(accountId, request));
    }

    @PostMapping(value = ApiEndpoints.TRANSFERS, consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Transfer funds between accounts",
            description =
                    "Atomically transfers funds from one account to another, creating paired"
                            + " TRANSFER_OUT and TRANSFER_IN entries.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Transfer executed successfully"),
        @ApiResponse(
                responseCode = "400",
                description = "Invalid transfer request or self-transfer attempt",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "404",
                description = "Source or destination account not found",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "422",
                description = "Insufficient funds in source account",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public void transfer(@Valid @RequestBody TransferRequest request) {
        bankingService.transfer(request);
    }
}
