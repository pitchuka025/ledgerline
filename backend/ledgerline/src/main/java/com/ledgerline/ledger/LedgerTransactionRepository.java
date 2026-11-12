package com.ledgerline.ledger;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LedgerTransactionRepository extends JpaRepository<LedgerTransaction, UUID> {
    Optional<LedgerTransaction> findByActorIdAndIdempotencyKey(UUID actorId, String idempotencyKey);
    Optional<LedgerTransaction> findByReference(String reference);
    List<LedgerTransaction> findTop100BySourceAccountIdOrDestinationAccountIdOrderByCreatedAtDesc(
            UUID sourceAccountId, UUID destinationAccountId);
    List<LedgerTransaction> findTop100ByOrderByCreatedAtDesc();
}
