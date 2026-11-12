package com.ledgerline.scheduledtransfer;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScheduledTransferRepository extends JpaRepository<ScheduledTransfer, UUID> {
    List<ScheduledTransfer> findByOwnerIdOrderByScheduledAtDesc(UUID ownerId);

    List<ScheduledTransfer> findTop50ByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAsc(
            ScheduledTransferStatus status, Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ScheduledTransfer s where s.id = :id")
    Optional<ScheduledTransfer> lockById(@Param("id") UUID id);
}
