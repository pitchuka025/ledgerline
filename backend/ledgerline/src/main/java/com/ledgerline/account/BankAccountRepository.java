package com.ledgerline.account;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BankAccountRepository extends JpaRepository<BankAccount, UUID> {
    List<BankAccount> findByOwnerIdOrderByCreatedAtAsc(UUID ownerId);
    Optional<BankAccount> findByAccountNumber(String accountNumber);
    Optional<BankAccount> findByAccountNumberIgnoreCase(String accountNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from BankAccount a where a.id = :id")
    Optional<BankAccount> lockById(@Param("id") UUID id);
}
