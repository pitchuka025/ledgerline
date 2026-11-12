package com.ledgerline.scheduledtransfer;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class ScheduledTransferDtos {
    private ScheduledTransferDtos() {
    }

    public record CreateRequest(
            @NotNull UUID sourceAccountId,
            @NotNull UUID destinationAccountId,
            @NotNull @DecimalMin(value = "0.01") @Digits(integer = 17, fraction = 2) BigDecimal amount,
            @NotNull @Future Instant scheduledAt) {
    }

    public record Response(UUID id, UUID sourceAccountId, UUID destinationAccountId, BigDecimal amount,
                           Instant scheduledAt, ScheduledTransferStatus status, String failureReason,
                           Instant createdAt, Instant updatedAt, Instant completedAt) {
        public static Response from(ScheduledTransfer transfer) {
            return new Response(transfer.getId(), transfer.getSourceAccount().getId(),
                    transfer.getDestinationAccount().getId(), transfer.getAmount(), transfer.getScheduledAt(),
                    transfer.getStatus(), transfer.getFailureReason(), transfer.getCreatedAt(),
                    transfer.getUpdatedAt(), transfer.getCompletedAt());
        }
    }
}
