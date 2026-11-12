package com.ledgerline.ledger;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ledger_entries")
public class LedgerEntry {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    private LedgerTransaction transaction;

    @Column(name = "account_ref", nullable = false, length = 64)
    private String accountRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 6)
    private EntryType entryType;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "account_balance_after", precision = 19, scale = 2)
    private BigDecimal accountBalanceAfter;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected LedgerEntry() {
    }

    public LedgerEntry(LedgerTransaction transaction, String accountRef, EntryType entryType,
                       BigDecimal amount, BigDecimal accountBalanceAfter) {
        this.id = UUID.randomUUID();
        this.transaction = transaction;
        this.accountRef = accountRef;
        this.entryType = entryType;
        this.amount = amount;
        this.accountBalanceAfter = accountBalanceAfter;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public LedgerTransaction getTransaction() { return transaction; }
    public String getAccountRef() { return accountRef; }
    public EntryType getEntryType() { return entryType; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getAccountBalanceAfter() { return accountBalanceAfter; }
    public Instant getCreatedAt() { return createdAt; }
}
