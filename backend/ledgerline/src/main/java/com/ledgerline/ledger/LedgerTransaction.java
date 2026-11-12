package com.ledgerline.ledger;

import com.ledgerline.user.AppUser;
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
@Table(name = "ledger_transactions")
public class LedgerTransaction {
    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 36)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionType type;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id", nullable = false)
    private AppUser actor;

    @Column(name = "source_account_id")
    private UUID sourceAccountId;

    @Column(name = "destination_account_id")
    private UUID destinationAccountId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 140)
    private String description;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "request_fingerprint", nullable = false, length = 64)
    private String requestFingerprint;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected LedgerTransaction() {
    }

    public LedgerTransaction(String reference, TransactionType type, AppUser actor, UUID sourceAccountId,
                             UUID destinationAccountId, BigDecimal amount, String description,
                             String idempotencyKey, String requestFingerprint) {
        this.id = UUID.randomUUID();
        this.reference = reference;
        this.type = type;
        this.actor = actor;
        this.sourceAccountId = sourceAccountId;
        this.destinationAccountId = destinationAccountId;
        this.amount = amount;
        this.description = description;
        this.idempotencyKey = idempotencyKey;
        this.requestFingerprint = requestFingerprint;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getReference() { return reference; }
    public TransactionType getType() { return type; }
    public AppUser getActor() { return actor; }
    public UUID getSourceAccountId() { return sourceAccountId; }
    public UUID getDestinationAccountId() { return destinationAccountId; }
    public BigDecimal getAmount() { return amount; }
    public String getDescription() { return description; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getRequestFingerprint() { return requestFingerprint; }
    public Instant getCreatedAt() { return createdAt; }
}
