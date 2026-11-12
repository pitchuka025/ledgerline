package com.ledgerline.account;

import com.ledgerline.common.ApiExceptions;
import com.ledgerline.audit.AuditEvent;
import com.ledgerline.audit.AuditEventRepository;
import com.ledgerline.user.AppUser;
import com.ledgerline.user.AppUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AccountService {
    private final BankAccountRepository accounts;
    private final AppUserRepository users;
    private final AuditEventRepository auditEvents;

    public AccountService(BankAccountRepository accounts, AppUserRepository users,
                          AuditEventRepository auditEvents) {
        this.accounts = accounts;
        this.users = users;
        this.auditEvents = auditEvents;
    }

    @Transactional
    public BankAccount createFor(AppUser owner, String currency) {
        return createFor(owner, currency, owner);
    }

    private BankAccount createFor(AppUser owner, String currency, AppUser actor) {
        if (!"USD".equals(currency)) {
            throw new IllegalArgumentException("Only USD accounts are currently supported");
        }
        String accountNumber = "LL" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 12).toUpperCase();
        BankAccount account = accounts.save(new BankAccount(owner, accountNumber, currency));
        auditEvents.save(new AuditEvent(actor, "ACCOUNT_CREATED", "ACCOUNT", account.getId().toString(),
                "USD account " + accountNumber + " created"));
        return account;
    }

    @Transactional
    public AccountDtos.AccountResponse createForCurrentUser(AppUser owner, AccountDtos.CreateAccountRequest request) {
        return AccountDtos.AccountResponse.from(createFor(owner, request.currencyOrDefault()));
    }

    @Transactional(readOnly = true)
    public List<AccountDtos.AccountResponse> listFor(AppUser requester) {
        List<BankAccount> records = requester.getRole().name().equals("CUSTOMER")
                ? accounts.findByOwnerIdOrderByCreatedAtAsc(requester.getId())
                : accounts.findAll();
        return records.stream().map(AccountDtos.AccountResponse::from).toList();
    }

    @Transactional
    public AccountDtos.AccountResponse createForEmail(String email, AccountDtos.AdminCreateAccountRequest request,
                                                        AppUser actor) {
        AppUser owner = users.findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> new ApiExceptions.NotFound("User not found"));
        return AccountDtos.AccountResponse.from(createFor(owner, request.currencyOrDefault(), actor));
    }

    @Transactional(readOnly = true)
    public BankAccount getOwned(UUID accountId, AppUser requester) {
        BankAccount account = accounts.findById(accountId)
                .orElseThrow(() -> new ApiExceptions.NotFound("Account not found"));
        if (requester.getRole().name().equals("CUSTOMER")
                && !account.getOwner().getId().equals(requester.getId())) {
            throw new ApiExceptions.Forbidden("You do not have access to this account");
        }
        return account;
    }

    @Transactional(readOnly = true)
    public List<UUID> ownedAccountIds(UUID ownerId) {
        return accounts.findByOwnerIdOrderByCreatedAtAsc(ownerId).stream().map(BankAccount::getId).toList();
    }
}
