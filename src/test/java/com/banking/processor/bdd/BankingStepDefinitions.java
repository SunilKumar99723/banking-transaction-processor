package com.banking.processor.bdd;

import static org.assertj.core.api.Assertions.assertThat;

import com.banking.processor.constant.ApiEndpoints;
import com.banking.processor.domain.TransactionType;
import com.banking.processor.dto.AccountResponse;
import com.banking.processor.dto.BalanceResponse;
import com.banking.processor.dto.CreateAccountRequest;
import com.banking.processor.dto.DepositRequest;
import com.banking.processor.dto.TransactionResponse;
import com.banking.processor.dto.TransferRequest;
import com.banking.processor.dto.WithdrawRequest;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.cucumber.spring.CucumberContextConfiguration;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@CucumberContextConfiguration
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class BankingStepDefinitions {

    @Autowired private TestRestTemplate restTemplate;

    private ResponseEntity<?> latestResponse;

    private BigDecimal parseRupees(String value) {
        String cleaned =
                value.replace("₹", "").replace("Rs.", "").replace("Rs", "").replace(",", "").trim();
        return new BigDecimal(cleaned);
    }

    @Given("an active account exists with ID {string} and initial balance of {word}")
    public void anActiveAccountExistsWithIDAndInitialBalance(String accountId, String balance) {
        CreateAccountRequest request = new CreateAccountRequest(accountId, parseRupees(balance));
        ResponseEntity<AccountResponse> response =
                restTemplate.postForEntity(
                        ApiEndpoints.BASE_ACCOUNTS, request, AccountResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        this.latestResponse = response;
    }

    @When("a deposit of {word} with reference {string} is made to {string}")
    public void aDepositWithReferenceIsMadeTo(String amount, String reference, String accountId) {
        DepositRequest request = new DepositRequest(parseRupees(amount), reference);
        String url =
                ApiEndpoints.BASE_ACCOUNTS
                        + ApiEndpoints.DEPOSITS.replace("{accountId}", accountId);

        this.latestResponse = restTemplate.postForEntity(url, request, TransactionResponse.class);
        assertThat(this.latestResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @When("attempting a withdrawal of {word} with reference {string} from {string}")
    public void attemptingAWithdrawalWithReferenceFrom(
            String amount, String reference, String accountId) {
        WithdrawRequest request = new WithdrawRequest(parseRupees(amount), reference);
        String url =
                ApiEndpoints.BASE_ACCOUNTS
                        + ApiEndpoints.WITHDRAWALS.replace("{accountId}", accountId);

        this.latestResponse = restTemplate.postForEntity(url, request, String.class);
    }

    @When("a transfer of {word} is executed from {string} to {string} with reference {string}")
    public void aTransferIsExecutedFromToWithReference(
            String amount, String source, String dest, String ref) {
        TransferRequest request = new TransferRequest(source, dest, parseRupees(amount), ref);
        String url = ApiEndpoints.BASE_ACCOUNTS + ApiEndpoints.TRANSFERS;

        this.latestResponse = restTemplate.postForEntity(url, request, Void.class);
        assertThat(this.latestResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @When("a transfer of {word} is attempted from {string} to {string}")
    public void aTransferIsAttemptedFromTo(String amount, String source, String dest) {
        TransferRequest request =
                new TransferRequest(source, dest, parseRupees(amount), "Self transfer check");
        String url = ApiEndpoints.BASE_ACCOUNTS + ApiEndpoints.TRANSFERS;

        this.latestResponse = restTemplate.postForEntity(url, request, String.class);
    }

    @Then("the cleared balance of {string} should be {word}")
    public void theClearedBalanceOfShouldBe(String accountId, String expectedBalance) {
        String url =
                ApiEndpoints.BASE_ACCOUNTS + ApiEndpoints.BALANCE.replace("{accountId}", accountId);
        ResponseEntity<BalanceResponse> response =
                restTemplate.getForEntity(url, BalanceResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().balance()).isEqualByComparingTo(parseRupees(expectedBalance));
    }

    @Then("the cleared balance of {string} should remain {word}")
    public void theClearedBalanceOfShouldRemain(String accountId, String expectedBalance) {
        theClearedBalanceOfShouldBe(accountId, expectedBalance);
    }

    @Then("the operation should fail due to insufficient funds")
    public void theOperationShouldFailDueToInsufficientFunds() {
        assertThat(this.latestResponse.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Then("the operation should fail due to an invalid self-transfer attempt")
    public void theOperationShouldFailDueToAnInvalidSelfTransferAttempt() {
        assertThat(this.latestResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @And("the transaction audit ledger for {string} should contain {int} entry")
    public void theTransactionAuditLedgerForShouldContainSingleEntry(String accountId, int count) {
        theTransactionAuditLedgerForShouldContainEntries(accountId, count);
    }

    @And("the transaction audit ledger for {string} should contain {int} entries")
    public void theTransactionAuditLedgerForShouldContainEntries(String accountId, int count) {
        List<TransactionResponse> txs = fetchTransactions(accountId);
        assertThat(txs).hasSize(count);
    }

    @And("the latest transaction for {string} should be of type {string} with amount {word}")
    public void theLatestTransactionShouldBeOfTypeWithAmount(
            String accountId, String type, String expectedAmount) {
        List<TransactionResponse> txs = fetchTransactions(accountId);
        assertThat(txs).isNotEmpty();
        TransactionResponse latest = txs.get(0);

        assertThat(latest.type()).isEqualTo(TransactionType.valueOf(type));
        assertThat(latest.amount()).isEqualByComparingTo(parseRupees(expectedAmount));
    }

    private List<TransactionResponse> fetchTransactions(String accountId) {
        String url =
                ApiEndpoints.BASE_ACCOUNTS
                        + ApiEndpoints.TRANSACTIONS.replace("{accountId}", accountId);
        ResponseEntity<List<TransactionResponse>> response =
                restTemplate.exchange(
                        url,
                        HttpMethod.GET,
                        HttpEntity.EMPTY,
                        new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }
}
