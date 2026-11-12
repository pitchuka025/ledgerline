package com.ledgerline.ledger;

import com.ledgerline.account.AccountService;
import com.ledgerline.account.BankAccount;
import com.ledgerline.account.BankAccountRepository;
import com.ledgerline.audit.AuditEvent;
import com.ledgerline.audit.AuditEventRepository;
import com.ledgerline.common.ApiExceptions;
import com.ledgerline.user.AppUser;
import com.ledgerline.user.UserRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class LedgerService {
    private final BankAccountRepository accounts;
    private final LedgerTransactionRepository transactions;
    private final LedgerEntryRepository entries;
    private final AuditEventRepository auditEvents;
    private final AccountService accountService;

    public LedgerService(BankAccountRepository accounts, LedgerTransactionRepository transactions,
                         LedgerEntryRepository entries, AuditEventRepository auditEvents,
                         AccountService accountService) {
        this.accounts = accounts;
        this.transactions = transactions;
        this.entries = entries;
        this.auditEvents = auditEvents;
        this.accountService = accountService;
    }

    @Transactional
    public TransactionDtos.TransactionResponse deposit(AppUser actor, UUID accountId, BigDecimal rawAmount,
                                                       String description, String idempotencyKey) {
        BigDecimal amount = checkedAmount(rawAmount);
        String memo = checkedDescription(description, "Cash deposit");
        String fingerprint = fingerprint(TransactionType.DEPOSIT, accountId, null, amount, memo);
        LedgerTransaction replay = findReplay(actor, idempotencyKey, fingerprint);
        if (replay != null) {
            return TransactionDtos.TransactionResponse.from(replay);
        }
        BankAccount account = lockOwned(accountId, actor);
        LedgerTransaction replayAfterLock = findReplay(actor, idempotencyKey, fingerprint);
        if (replayAfterLock != null) {
            return TransactionDtos.TransactionResponse.from(replayAfterLock);
        }
        LedgerTransaction transaction = createTransaction(actor, TransactionType.DEPOSIT, null, accountId,
                amount, memo, idempotencyKey, fingerprint);
        transactions.flush();
        account.credit(amount);
        entries.saveAll(List.of(
                new LedgerEntry(transaction, account.getId().toString(), EntryType.CREDIT, amount, account.getBalance()),
                new LedgerEntry(transaction, "EXTERNAL:USD:CLEARING", EntryType.DEBIT, amount, null)));
        audit(actor, transaction);
        return TransactionDtos.TransactionResponse.from(transaction);
    }

    @Transactional
    public TransactionDtos.TransactionResponse withdraw(AppUser actor, UUID accountId, BigDecimal rawAmount,
                                                        String description, String idempotencyKey) {
        BigDecimal amount = checkedAmount(rawAmount);
        String memo = checkedDescription(description, "Cash withdrawal");
        String fingerprint = fingerprint(TransactionType.WITHDRAWAL, accountId, null, amount, memo);
        LedgerTransaction replay = findReplay(actor, idempotencyKey, fingerprint);
        if (replay != null) {
            return TransactionDtos.TransactionResponse.from(replay);
        }
        BankAccount account = lockOwned(accountId, actor);
        LedgerTransaction replayAfterLock = findReplay(actor, idempotencyKey, fingerprint);
        if (replayAfterLock != null) {
            return TransactionDtos.TransactionResponse.from(replayAfterLock);
        }
        LedgerTransaction transaction = createTransaction(actor, TransactionType.WITHDRAWAL, accountId, null,
                amount, memo, idempotencyKey, fingerprint);
        transactions.flush();
        debit(account, amount);
        entries.saveAll(List.of(
                new LedgerEntry(transaction, account.getId().toString(), EntryType.DEBIT, amount, account.getBalance()),
                new LedgerEntry(transaction, "EXTERNAL:USD:CLEARING", EntryType.CREDIT, amount, null)));
        audit(actor, transaction);
        return TransactionDtos.TransactionResponse.from(transaction);
    }

    @Transactional
    public TransactionDtos.TransactionResponse transfer(AppUser actor, UUID sourceAccountId,
                                                       String destinationAccountNumber, BigDecimal rawAmount,
                                                       String description, String idempotencyKey) {
        BigDecimal amount = checkedAmount(rawAmount);
        String normalizedAccountNumber = normalizeAccountNumber(destinationAccountNumber);
        BankAccount destinationLookup = accounts.findByAccountNumberIgnoreCase(normalizedAccountNumber)
                .orElseThrow(() -> new ApiExceptions.NotFound("Destination account not found"));
        UUID destinationAccountId = destinationLookup.getId();
        if (sourceAccountId.equals(destinationAccountId)) {
            throw new IllegalArgumentException("Source and destination accounts must be different");
        }
        String memo = checkedDescription(description, "Account transfer");
        String fingerprint = fingerprint(TransactionType.TRANSFER, sourceAccountId, destinationAccountId, amount, memo);
        LedgerTransaction replay = findReplay(actor, idempotencyKey, fingerprint);
        if (replay != null) {
            return TransactionDtos.TransactionResponse.from(replay);
        }

        Map<UUID, BankAccount> lockedAccounts = new LinkedHashMap<>();
        List.of(sourceAccountId, destinationAccountId).stream().sorted(Comparator.naturalOrder()).forEach(id -> {
            BankAccount account = accounts.lockById(id)
                    .orElseThrow(() -> new ApiExceptions.NotFound("Account not found"));
            lockedAccounts.put(id, account);
        });
        BankAccount source = lockedAccounts.get(sourceAccountId);
        BankAccount destination = lockedAccounts.get(destinationAccountId);
        requireOwner(source, actor);
        if (!source.getCurrency().equals(destination.getCurrency())) {
            throw new IllegalArgumentException("Transfers between different currencies are not supported");
        }
        LedgerTransaction replayAfterLock = findReplay(actor, idempotencyKey, fingerprint);
        if (replayAfterLock != null) {
            return TransactionDtos.TransactionResponse.from(replayAfterLock);
        }
        LedgerTransaction transaction = createTransaction(actor, TransactionType.TRANSFER, sourceAccountId,
                destinationAccountId, amount, memo, idempotencyKey, fingerprint);
        transactions.flush();
        debit(source, amount);
        destination.credit(amount);
        entries.saveAll(List.of(
                new LedgerEntry(transaction, source.getId().toString(), EntryType.DEBIT, amount, source.getBalance()),
                new LedgerEntry(transaction, destination.getId().toString(), EntryType.CREDIT, amount,
                        destination.getBalance())));
        audit(actor, transaction);
        return TransactionDtos.TransactionResponse.from(transaction);
    }

    @Transactional(readOnly = true)
    public List<TransactionDtos.TransactionResponse> statement(UUID accountId, AppUser requester) {
        BankAccount account = accountService.getOwned(accountId, requester);
        List<LedgerTransaction> records = transactions
                .findTop100BySourceAccountIdOrDestinationAccountIdOrderByCreatedAtDesc(accountId, accountId);
        return records.stream().filter(transaction ->
                        requester.getRole() != UserRole.CUSTOMER
                                || account.getOwner().getId().equals(requester.getId()))
                .map(TransactionDtos.TransactionResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<TransactionDtos.TransactionResponse> recent(AppUser requester) {
        List<LedgerTransaction> records;
        if (requester.getRole() == UserRole.CUSTOMER) {
            Map<UUID, LedgerTransaction> unique = new LinkedHashMap<>();
            for (UUID accountId : accountService.ownedAccountIds(requester.getId())) {
                transactions.findTop100BySourceAccountIdOrDestinationAccountIdOrderByCreatedAtDesc(accountId, accountId)
                        .forEach(transaction -> unique.put(transaction.getId(), transaction));
            }
            records = new ArrayList<>(unique.values());
            records.sort(Comparator.comparing(LedgerTransaction::getCreatedAt).reversed());
            records = records.stream().limit(100).toList();
        } else {
            records = transactions.findTop100ByOrderByCreatedAtDesc();
        }
        return records.stream().map(TransactionDtos.TransactionResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<TransactionDtos.LedgerRecordResponse> ledgerRecords(AppUser requester) {
        List<UUID> accountIds = requester.getRole() == UserRole.CUSTOMER
                ? accountService.ownedAccountIds(requester.getId())
                : accounts.findAll().stream().map(BankAccount::getId).toList();
        if (accountIds.isEmpty()) {
            return List.of();
        }
        List<String> accountRefs = accountIds.stream().map(UUID::toString).toList();
        return entries.findTop100ByAccountRefInOrderByCreatedAtDesc(accountRefs).stream()
                .map(TransactionDtos.LedgerRecordResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public TransactionDtos.LedgerProofResponse proof(String reference, AppUser requester) {
        LedgerTransaction transaction = transactions.findByReference(reference)
                .orElseThrow(() -> new ApiExceptions.NotFound("Transaction not found"));
        if (requester.getRole() == UserRole.CUSTOMER) {
            boolean ownsSource = transaction.getSourceAccountId() != null
                    && accounts.findById(transaction.getSourceAccountId())
                    .map(account -> account.getOwner().getId().equals(requester.getId())).orElse(false);
            boolean ownsDestination = transaction.getDestinationAccountId() != null
                    && accounts.findById(transaction.getDestinationAccountId())
                    .map(account -> account.getOwner().getId().equals(requester.getId())).orElse(false);
            if (!ownsSource && !ownsDestination) {
                throw new ApiExceptions.Forbidden("You do not have access to this transaction");
            }
        }
        List<LedgerEntry> transactionEntries = entries.findByTransactionIdOrderByCreatedAtAsc(transaction.getId());
        BigDecimal debits = totalFor(transactionEntries, EntryType.DEBIT);
        BigDecimal credits = totalFor(transactionEntries, EntryType.CREDIT);
        return new TransactionDtos.LedgerProofResponse(transaction.getReference(), debits, credits,
                debits.compareTo(credits) == 0,
                transactionEntries.stream().map(TransactionDtos.LedgerEntryResponse::from).toList());
    }

    private BankAccount lockOwned(UUID accountId, AppUser actor) {
        BankAccount account = accounts.lockById(accountId)
                .orElseThrow(() -> new ApiExceptions.NotFound("Account not found"));
        requireOwner(account, actor);
        return account;
    }

    private void requireOwner(BankAccount account, AppUser actor) {
        if (actor.getRole() == UserRole.CUSTOMER && !account.getOwner().getId().equals(actor.getId())) {
            throw new ApiExceptions.Forbidden("You do not have access to this account");
        }
    }

    private void debit(BankAccount account, BigDecimal amount) {
        try {
            account.debit(amount);
        } catch (IllegalArgumentException exception) {
            throw new ApiExceptions.Conflict(exception.getMessage());
        }
    }

    private BigDecimal checkedAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        try {
            BigDecimal normalized = amount.setScale(2, java.math.RoundingMode.UNNECESSARY);
            if (normalized.precision() > 19) {
                throw new IllegalArgumentException("Amount exceeds the supported limit");
            }
            return normalized;
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Amount cannot have more than two decimal places");
        }
    }

    private String checkedDescription(String description, String fallback) {
        String value = description == null || description.isBlank() ? fallback : description.trim();
        if (value.length() > 140) {
            throw new IllegalArgumentException("Description must be at most 140 characters");
        }
        return value;
    }

    private String normalizeAccountNumber(String accountNumber) {
        String value = accountNumber == null ? "" : accountNumber.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Destination account number is required");
        }
        return value.toUpperCase(Locale.ROOT);
    }

    private LedgerTransaction findReplay(AppUser actor, String key, String fingerprint) {
        if (key == null || key.isBlank() || key.length() > 100) {
            throw new IllegalArgumentException("Idempotency-Key must contain 1 to 100 characters");
        }
        return transactions.findByActorIdAndIdempotencyKey(actor.getId(), key)
                .map(existing -> {
                    if (!existing.getRequestFingerprint().equals(fingerprint)) {
                        throw new ApiExceptions.Conflict("Idempotency key was already used for a different request");
                    }
                    return existing;
                }).orElse(null);
    }

    private LedgerTransaction createTransaction(AppUser actor, TransactionType type, UUID source, UUID destination,
                                                BigDecimal amount, String description, String key,
                                                String fingerprint) {
        return transactions.save(new LedgerTransaction(UUID.randomUUID().toString(), type, actor, source, destination,
                amount, description, key, fingerprint));
    }

    private void audit(AppUser actor, LedgerTransaction transaction) {
        auditEvents.save(new AuditEvent(actor, transaction.getType().name(), "TRANSACTION",
                transaction.getReference(), transaction.getAmount().toPlainString() + " USD"));
    }

    private String fingerprint(TransactionType type, UUID source, UUID destination, BigDecimal amount,
                               String description) {
        String canonical = String.join("|", type.name(), String.valueOf(source), String.valueOf(destination),
                amount.toPlainString(), description);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private BigDecimal totalFor(List<LedgerEntry> transactionEntries, EntryType type) {
        return transactionEntries.stream().filter(entry -> entry.getEntryType() == type)
                .map(LedgerEntry::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
