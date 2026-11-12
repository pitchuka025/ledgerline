package com.ledgerline.account;

import com.ledgerline.user.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bank_accounts")
public class BankAccount {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private AppUser owner;

    @Column(name = "account_number", nullable = false, unique = true, length = 32)
    private String accountNumber;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected BankAccount() {
    }

    public BankAccount(AppUser owner, String accountNumber, String currency) {
        this.id = UUID.randomUUID();
        this.owner = owner;
        this.accountNumber = accountNumber;
        this.currency = currency;
        this.balance = new BigDecimal("0.00");
        this.createdAt = Instant.now();
    }

    public void credit(BigDecimal amount) { balance = balance.add(amount); }
    public void debit(BigDecimal amount) {
        if (balance.compareTo(amount) < 0) {
            throw new IllegalArgumentException("Insufficient funds");
        }
        balance = balance.subtract(amount);
    }

    public UUID getId() { return id; }
    public AppUser getOwner() { return owner; }
    public String getAccountNumber() { return accountNumber; }
    public String getCurrency() { return currency; }
    public BigDecimal getBalance() { return balance; }
    public Instant getCreatedAt() { return createdAt; }
}
