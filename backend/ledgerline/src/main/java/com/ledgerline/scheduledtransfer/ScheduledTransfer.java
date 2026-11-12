package com.ledgerline.scheduledtransfer;

import com.ledgerline.account.BankAccount;
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
@Table(name = "scheduled_transfers")
public class ScheduledTransfer {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private AppUser owner;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_account_id", nullable = false)
    private BankAccount sourceAccount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "destination_account_id", nullable = false)
    private BankAccount destinationAccount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ScheduledTransferStatus status;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected ScheduledTransfer() {
    }

    public ScheduledTransfer(AppUser owner, BankAccount sourceAccount, BankAccount destinationAccount,
                             BigDecimal amount, Instant scheduledAt) {
        this.id = UUID.randomUUID();
        this.owner = owner;
        this.sourceAccount = sourceAccount;
        this.destinationAccount = destinationAccount;
        this.amount = amount;
        this.scheduledAt = scheduledAt;
        this.status = ScheduledTransferStatus.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void complete(Instant now) {
        status = ScheduledTransferStatus.COMPLETED;
        completedAt = now;
        updatedAt = now;
        failureReason = null;
    }

    public void fail(String reason, Instant now) {
        status = ScheduledTransferStatus.FAILED;
        failureReason = reason;
        updatedAt = now;
    }

    public void cancel(Instant now) {
        status = ScheduledTransferStatus.CANCELLED;
        updatedAt = now;
    }

    public UUID getId() { return id; }
    public AppUser getOwner() { return owner; }
    public BankAccount getSourceAccount() { return sourceAccount; }
    public BankAccount getDestinationAccount() { return destinationAccount; }
    public BigDecimal getAmount() { return amount; }
    public Instant getScheduledAt() { return scheduledAt; }
    public ScheduledTransferStatus getStatus() { return status; }
    public String getFailureReason() { return failureReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getCompletedAt() { return completedAt; }
}
