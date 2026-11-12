package com.ledgerline.ledger;

import com.ledgerline.user.AppUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class LedgerController {
    private final LedgerService ledger;

    public LedgerController(LedgerService ledger) {
        this.ledger = ledger;
    }

    @PostMapping("/accounts/{accountId}/deposit")
    @PreAuthorize("hasRole('CUSTOMER')")
    public TransactionDtos.TransactionResponse deposit(@PathVariable UUID accountId,
            @AuthenticationPrincipal AppUser user, @Valid @RequestBody TransactionDtos.MoneyRequest request,
            @RequestHeader("Idempotency-Key") String key) {
        return ledger.deposit(user, accountId, request.amount(), request.description(), key);
    }

    @PostMapping("/accounts/{accountId}/withdraw")
    @PreAuthorize("hasRole('CUSTOMER')")
    public TransactionDtos.TransactionResponse withdraw(@PathVariable UUID accountId,
            @AuthenticationPrincipal AppUser user, @Valid @RequestBody TransactionDtos.MoneyRequest request,
            @RequestHeader("Idempotency-Key") String key) {
        return ledger.withdraw(user, accountId, request.amount(), request.description(), key);
    }

    @PostMapping("/accounts/{accountId}/transfer")
    @PreAuthorize("hasRole('CUSTOMER')")
    public TransactionDtos.TransactionResponse transfer(@PathVariable UUID accountId,
            @AuthenticationPrincipal AppUser user, @Valid @RequestBody TransactionDtos.TransferRequest request,
            @RequestHeader("Idempotency-Key") String key) {
        return ledger.transfer(user, accountId, request.destinationAccountNumber(), request.amount(),
                request.description(), key);
    }

    @GetMapping("/accounts/{accountId}/transactions")
    public List<TransactionDtos.TransactionResponse> statement(@PathVariable UUID accountId,
            @AuthenticationPrincipal AppUser user) {
        return ledger.statement(accountId, user);
    }

    @GetMapping("/transactions")
    public List<TransactionDtos.TransactionResponse> recent(@AuthenticationPrincipal AppUser user) {
        return ledger.recent(user);
    }

    @GetMapping("/ledger")
    public List<TransactionDtos.LedgerRecordResponse> ledgerRecords(@AuthenticationPrincipal AppUser user) {
        return ledger.ledgerRecords(user);
    }

    @GetMapping("/transactions/{reference}/proof")
    public TransactionDtos.LedgerProofResponse proof(@PathVariable String reference,
            @AuthenticationPrincipal AppUser user) {
        return ledger.proof(reference, user);
    }
}
