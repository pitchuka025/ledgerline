package com.ledgerline.account;

import com.ledgerline.user.AppUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class AccountController {
    private final AccountService accounts;

    public AccountController(AccountService accounts) {
        this.accounts = accounts;
    }

    @GetMapping("/accounts")
    public List<AccountDtos.AccountResponse> list(@AuthenticationPrincipal AppUser user) {
        return accounts.listFor(user);
    }

    @PostMapping("/accounts")
    @PreAuthorize("hasRole('CUSTOMER')")
    public AccountDtos.AccountResponse create(@AuthenticationPrincipal AppUser user,
                                               @Valid @RequestBody AccountDtos.CreateAccountRequest request) {
        return accounts.createForCurrentUser(user, request);
    }

    @PostMapping("/admin/accounts")
    @PreAuthorize("hasRole('ADMIN')")
    public AccountDtos.AccountResponse createForUser(@AuthenticationPrincipal AppUser user,
            @Valid @RequestBody AccountDtos.AdminCreateAccountRequest request) {
        return accounts.createForEmail(request.email(), request, user);
    }
}
