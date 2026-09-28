package com.banking.processor.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.banking.processor.domain.Account;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

@DataJpaTest
@DisplayName("AccountRepository Persistence & Locking Specifications")
class AccountRepositoryTest {

    @Autowired private AccountRepository accountRepository;

    @Autowired private TestEntityManager entityManager;

    @Test
    @DisplayName("Should successfully persist and find account by ID")
    void shouldPersistAndFindAccountById() {
        Account account = new Account("ACC-101", new BigDecimal("15000.00"));
        accountRepository.save(account);

        entityManager.flush();
        entityManager.clear();

        Optional<Account> found = accountRepository.findById("ACC-101");
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo("ACC-101");
        assertThat(found.get().getBalance()).isEqualByComparingTo(new BigDecimal("15000.00"));
    }

    @Test
    @DisplayName("Should fetch account with pessimistic write lock (findByIdForUpdate)")
    void shouldFindAccountByIdForUpdate() {
        Account account = new Account("ACC-102", new BigDecimal("25000.00"));
        accountRepository.save(account);

        entityManager.flush();
        entityManager.clear();

        Optional<Account> lockedAccount = accountRepository.findByIdForUpdate("ACC-102");
        assertThat(lockedAccount).isPresent();
        assertThat(lockedAccount.get().getId()).isEqualTo("ACC-102");
        assertThat(lockedAccount.get().getBalance())
                .isEqualByComparingTo(new BigDecimal("25000.00"));
    }

    @Test
    @DisplayName("Should return empty optional when finding non-existent account ID")
    void shouldReturnEmptyWhenAccountNotFound() {
        Optional<Account> result = accountRepository.findByIdForUpdate("NON-EXISTENT");
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should correctly verify account existence by ID")
    void shouldVerifyExistsById() {
        Account account = new Account("ACC-103", new BigDecimal("500.00"));
        accountRepository.save(account);

        entityManager.flush();

        assertThat(accountRepository.existsById("ACC-103")).isTrue();
        assertThat(accountRepository.existsById("ACC-999")).isFalse();
    }
}
