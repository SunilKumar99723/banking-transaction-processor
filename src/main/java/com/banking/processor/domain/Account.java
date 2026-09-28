package com.banking.processor.domain;

import com.banking.processor.exception.InsufficientFundsException;
import com.banking.processor.exception.InvalidAmountException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.DynamicUpdate;

@Entity
@Table(name = "accounts")
@DynamicUpdate
@Getter
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Account {

    @Id
    @EqualsAndHashCode.Include
    @Column(name = "account_id", nullable = false, updatable = false, length = 64)
    private String id;

    @Column(name = "balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal balance;

    @Version
    @Column(name = "version")
    private Long version;

    public Account(String id, BigDecimal initialBalance) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Account ID cannot be null or blank");
        }
        Objects.requireNonNull(initialBalance, "Initial balance cannot be null");
        validateAmountIsPositiveOrZero(initialBalance);

        this.id = id.trim();
        this.balance = initialBalance.setScale(2, RoundingMode.HALF_EVEN);
    }

    public void credit(BigDecimal amount) {
        validatePositiveAmount(amount);
        this.balance = this.balance.add(amount.setScale(2, RoundingMode.HALF_EVEN));
    }

    public void debit(BigDecimal amount) {
        validatePositiveAmount(amount);
        BigDecimal normalizedAmount = amount.setScale(2, RoundingMode.HALF_EVEN);
        if (normalizedAmount.compareTo(this.balance) > 0) {
            throw new InsufficientFundsException(
                    "Account "
                            + this.id
                            + " has insufficient balance: "
                            + this.balance
                            + ", requested: "
                            + normalizedAmount);
        }
        this.balance = this.balance.subtract(normalizedAmount);
    }

    private void validatePositiveAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidAmountException(
                    "Transaction amount must be strictly greater than zero");
        }
    }

    private void validateAmountIsPositiveOrZero(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidAmountException("Initial balance cannot be negative");
        }
    }
}
