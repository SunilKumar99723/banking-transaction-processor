package com.banking.processor.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.banking.processor.exception.InsufficientFundsException;
import com.banking.processor.exception.InvalidAmountException;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Account Aggregate Invariants")
class AccountTest {

    @Test
    @DisplayName("Should initialize account with rounded 2-decimal scale")
    void shouldCreateAccountWithValidScale() {
        Account account = new Account("ACC-100", new BigDecimal("50.555"));
        assertThat(account.getBalance()).isEqualTo(new BigDecimal("50.56"));
    }

    @Test
    @DisplayName("Should reject negative initial balances")
    void shouldRejectNegativeInitialBalance() {
        assertThatThrownBy(() -> new Account("ACC-100", new BigDecimal("-1.00")))
                .isInstanceOf(InvalidAmountException.class)
                .hasMessageContaining("Initial balance cannot be negative");
    }

    @Test
    @DisplayName("Should successfully credit balance on deposit")
    void shouldCreditBalance() {
        Account account = new Account("ACC-100", new BigDecimal("100.00"));
        account.credit(new BigDecimal("50.25"));
        assertThat(account.getBalance()).isEqualTo(new BigDecimal("150.25"));
    }

    @Test
    @DisplayName("Should reject zero or negative credit amounts")
    void shouldRejectZeroOrNegativeCredit() {
        Account account = new Account("ACC-100", new BigDecimal("100.00"));

        assertThatThrownBy(() -> account.credit(BigDecimal.ZERO))
                .isInstanceOf(InvalidAmountException.class);

        assertThatThrownBy(() -> account.credit(new BigDecimal("-10.00")))
                .isInstanceOf(InvalidAmountException.class);
    }

    @Test
    @DisplayName("Should throw InsufficientFundsException when debit exceeds balance")
    void shouldPreventOverdraft() {
        Account account = new Account("ACC-100", new BigDecimal("50.00"));

        assertThatThrownBy(() -> account.debit(new BigDecimal("50.01")))
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessageContaining("insufficient balance");

        assertThat(account.getBalance()).isEqualTo(new BigDecimal("50.00"));
    }
}
