package com.ledgerline.scheduledtransfer;

import com.ledgerline.common.ApiExceptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ScheduledTransferExecutionService {
    private static final Logger logger = LoggerFactory.getLogger(ScheduledTransferExecutionService.class);
    private final ScheduledTransferRepository transfers;
    private final ScheduledTransferPostingService posting;

    public ScheduledTransferExecutionService(ScheduledTransferRepository transfers,
                                             ScheduledTransferPostingService posting) {
        this.transfers = transfers;
        this.posting = posting;
    }

    @Transactional(readOnly = true)
    public List<UUID> dueTransferIds(Instant now) {
        return transfers.findTop50ByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAsc(
                        ScheduledTransferStatus.PENDING, now)
                .stream().map(ScheduledTransfer::getId).toList();
    }

    @Transactional
    public void execute(UUID id) {
        ScheduledTransfer transfer = transfers.lockById(id)
                .orElseThrow(() -> new ApiExceptions.NotFound("Scheduled transfer not found"));
        if (transfer.getStatus() != ScheduledTransferStatus.PENDING
                || transfer.getScheduledAt().isAfter(Instant.now())) {
            return;
        }
        try {
            posting.post(transfer.getOwner(), transfer.getId(), transfer.getSourceAccount().getId(),
                    transfer.getDestinationAccount().getAccountNumber(), transfer.getAmount());
            transfer.complete(Instant.now());
        } catch (RuntimeException exception) {
            String reason = failureReason(exception);
            transfer.fail(reason, Instant.now());
            logger.error("Scheduled transfer {} failed: {}", transfer.getId(), reason, exception);
        }
    }

    private String failureReason(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = exception.getClass().getSimpleName();
        }
        return message.length() <= 500 ? message : message.substring(0, 500);
    }
}
