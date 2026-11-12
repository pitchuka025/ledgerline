package com.ledgerline.ledger;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.List;

public final class TransactionDtos {
    private TransactionDtos() {
    }

    public record MoneyRequest(@NotNull BigDecimal amount, @Size(max = 140) String description) {
    }

    public record TransferRequest(
            @NotNull BigDecimal amount,
            @NotBlank @Size(max = 32) String destinationAccountNumber,
            @Size(max = 140) String description) {
    }

    public record TransactionResponse(UUID id, String reference, TransactionType type, UUID sourceAccountId,
                                     UUID destinationAccountId, BigDecimal amount, String description,
                                     Instant createdAt) {
        public static TransactionResponse from(LedgerTransaction transaction) {
            return new TransactionResponse(transaction.getId(), transaction.getReference(), transaction.getType(),
                    transaction.getSourceAccountId(), transaction.getDestinationAccountId(),
                    transaction.getAmount(), transaction.getDescription(), transaction.getCreatedAt());
        }
    }

    public record LedgerEntryResponse(String accountRef, EntryType entryType, BigDecimal amount,
                                      BigDecimal accountBalanceAfter, Instant createdAt) {
        public static LedgerEntryResponse from(LedgerEntry entry) {
            return new LedgerEntryResponse(entry.getAccountRef(), entry.getEntryType(), entry.getAmount(),
                    entry.getAccountBalanceAfter(), entry.getCreatedAt());
        }
    }

    public record LedgerRecordResponse(String reference, Instant createdAt, String accountRef,
            EntryType entryType, BigDecimal amount, BigDecimal accountBalanceAfter) {
        public static LedgerRecordResponse from(LedgerEntry entry) {
            return new LedgerRecordResponse(entry.getTransaction().getReference(), entry.getCreatedAt(),
                    entry.getAccountRef(), entry.getEntryType(), entry.getAmount(),
                    entry.getAccountBalanceAfter());
        }
    }

    public record LedgerProofResponse(String reference, BigDecimal totalDebits, BigDecimal totalCredits,
                                      boolean balanced, List<LedgerEntryResponse> entries) {
    }
}
