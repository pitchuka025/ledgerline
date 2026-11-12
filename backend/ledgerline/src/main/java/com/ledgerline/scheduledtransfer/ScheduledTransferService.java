package com.ledgerline.scheduledtransfer;

import com.ledgerline.account.BankAccount;
import com.ledgerline.account.BankAccountRepository;
import com.ledgerline.audit.AuditEvent;
import com.ledgerline.audit.AuditEventRepository;
import com.ledgerline.common.ApiExceptions;
import com.ledgerline.user.AppUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ScheduledTransferService {
    private final ScheduledTransferRepository transfers;
    private final BankAccountRepository accounts;
    private final AuditEventRepository auditEvents;

    public ScheduledTransferService(ScheduledTransferRepository transfers, BankAccountRepository accounts,
            AuditEventRepository auditEvents) {
        this.transfers = transfers;
        this.accounts = accounts;
        this.auditEvents = auditEvents;
    }

    @Transactional(readOnly = true)
    public List<ScheduledTransferDtos.Response> list(AppUser owner) {
        return transfers.findByOwnerIdOrderByScheduledAtDesc(owner.getId()).stream()
                .map(ScheduledTransferDtos.Response::from).toList();
    }

    @Transactional
    public ScheduledTransferDtos.Response create(AppUser owner, ScheduledTransferDtos.CreateRequest request) {
        if (!request.scheduledAt().isAfter(Instant.now())) {
            throw new IllegalArgumentException("Scheduled time must be in the future");
        }
        BigDecimal amount = checkedAmount(request.amount());
        if (request.sourceAccountId().equals(request.destinationAccountId())) {
            throw new IllegalArgumentException("Source and destination accounts must be different");
        }
        BankAccount source = accounts.findById(request.sourceAccountId())
                .orElseThrow(() -> new ApiExceptions.NotFound("Source account not found"));
        if (!source.getOwner().getId().equals(owner.getId())) {
            throw new ApiExceptions.Forbidden("You do not have access to the source account");
        }
        BankAccount destination = accounts.findById(request.destinationAccountId())
                .orElseThrow(() -> new ApiExceptions.NotFound("Destination account not found"));
        if (!"USD".equals(source.getCurrency()) || !"USD".equals(destination.getCurrency())) {
            throw new IllegalArgumentException("Scheduled transfers require USD accounts");
        }
        ScheduledTransfer transfer = transfers.saveAndFlush(
                new ScheduledTransfer(owner, source, destination, amount, request.scheduledAt()));
        auditEvents.save(new AuditEvent(owner, "SCHEDULED_TRANSFER_CREATED", "SCHEDULED_TRANSFER",
                transfer.getId().toString(), amount.toPlainString() + " USD scheduled"));
        return ScheduledTransferDtos.Response.from(transfer);
    }

    @Transactional
    public ScheduledTransferDtos.Response cancel(UUID id, AppUser owner) {
        ScheduledTransfer transfer = transfers.lockById(id)
                .orElseThrow(() -> new ApiExceptions.NotFound("Scheduled transfer not found"));
        requireOwner(transfer, owner);
        if (transfer.getStatus() != ScheduledTransferStatus.PENDING) {
            throw new ApiExceptions.Conflict("Only pending scheduled transfers can be cancelled");
        }
        transfer.cancel(Instant.now());
        auditEvents.save(new AuditEvent(owner, "SCHEDULED_TRANSFER_CANCELLED", "SCHEDULED_TRANSFER",
                transfer.getId().toString(), "Pending transfer cancelled"));
        return ScheduledTransferDtos.Response.from(transfer);
    }

    private BigDecimal checkedAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        try {
            BigDecimal normalized = amount.setScale(2, RoundingMode.UNNECESSARY);
            if (normalized.precision() > 19) {
                throw new IllegalArgumentException("Amount exceeds the supported limit");
            }
            return normalized;
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Amount cannot have more than two decimal places");
        }
    }

    static void requireOwner(ScheduledTransfer transfer, AppUser owner) {
        if (!transfer.getOwner().getId().equals(owner.getId())) {
            throw new ApiExceptions.Forbidden("You do not have access to this scheduled transfer");
        }
    }
}
