Feature: Banking Account & Transaction Processing
  As a retail banking customer
  I want to deposit, withdraw, and transfer funds
  So that I can manage my account balances and maintain an auditable transaction ledger

  Scenario: Successfully deposit funds into an account
    Given an active account exists with ID "ACC-ID-01" and initial balance of ₹25,000.00
    When a deposit of ₹5,500.00 with reference "Client consulting fee" is made to "ACC-ID-01"
    Then the cleared balance of "ACC-ID-01" should be ₹30,500.00
    And the transaction audit ledger for "ACC-ID-01" should contain 2 entries
    And the latest transaction for "ACC-ID-01" should be of type "DEPOSIT" with amount ₹5,500.00

  Scenario: Prevent unauthorized overdraft during withdrawal
    Given an active account exists with ID "ACC-ID-02" and initial balance of ₹1,500.00
    When attempting a withdrawal of ₹5,000.00 with reference "ATM cash withdrawal" from "ACC-ID-02"
    Then the operation should fail due to insufficient funds
    And the cleared balance of "ACC-ID-02" should remain ₹1,500.00
    And the transaction audit ledger for "ACC-ID-02" should contain 1 entry

  Scenario: Atomically transfer funds between two distinct accounts
    Given an active account exists with ID "ACC-ID-03" and initial balance of ₹50,000.00
    And an active account exists with ID "ACC-ID-04" and initial balance of ₹12,000.00
    When a transfer of ₹15,000.00 is executed from "ACC-ID-03" to "ACC-ID-04" with reference "Vendor advance payment"
    Then the cleared balance of "ACC-ID-03" should be ₹35,000.00
    And the cleared balance of "ACC-ID-04" should be ₹27,000.00
    And the latest transaction for "ACC-ID-03" should be of type "TRANSFER_OUT" with amount ₹15,000.00
    And the latest transaction for "ACC-ID-04" should be of type "TRANSFER_IN" with amount ₹15,000.00

  Scenario: Prevent circular self-transfers
    Given an active account exists with ID "ACC-ID-05" and initial balance of ₹10,000.00
    When a transfer of ₹2,000.00 is attempted from "ACC-ID-05" to "ACC-ID-05"
    Then the operation should fail due to an invalid self-transfer attempt
    And the cleared balance of "ACC-ID-05" should remain ₹10,000.00