package com.ledgerline.ledger;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {
    List<LedgerEntry> findByTransactionIdOrderByCreatedAtAsc(UUID transactionId);
    List<LedgerEntry> findTop100ByAccountRefInOrderByCreatedAtDesc(List<String> accountRefs);
}
