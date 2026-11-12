package com.ledgerline.admin;

import com.ledgerline.account.AccountService;
import com.ledgerline.account.BankAccount;
import com.ledgerline.ledger.LedgerService;
import com.ledgerline.user.AppUser;
import com.ledgerline.user.AppUserRepository;
import com.ledgerline.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

class DemoDataBootstrapTest {
    @Test
    void createsAccountsAndPostsDemoTransactionsWhenConfigured() {
        AppUserRepository users = mock(AppUserRepository.class);
        AccountService accountService = mock(AccountService.class);
        LedgerService ledger = mock(LedgerService.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        AppUser user = new AppUser("demo@example.test", "encoded-password", UserRole.CUSTOMER);
        BankAccount checking = new BankAccount(user, "LLCHECKING123", "USD");
        BankAccount savings = new BankAccount(user, "LLSAVINGS1234", "USD");

        when(users.existsByEmailIgnoreCase("demo@example.test")).thenReturn(false);
        when(users.save(any(AppUser.class))).thenReturn(user);
        when(passwordEncoder.encode("private-demo-password")).thenReturn("encoded-password");
        when(accountService.createFor(same(user), eq("USD"))).thenReturn(checking, savings);

        new DemoDataBootstrap(users, accountService, ledger, passwordEncoder,
                " Demo@Example.Test ", "private-demo-password").run(new DefaultApplicationArguments());

        verify(ledger).deposit(same(user), eq(checking.getId()), eq(new BigDecimal("45000.00")),
                eq("Demo salary credit"), startsWith("demo-seed-" + user.getId()));
        verify(ledger).deposit(same(user), eq(checking.getId()), eq(new BigDecimal("1200.00")),
                eq("Demo refund"), startsWith("demo-seed-" + user.getId()));
        verify(ledger).transfer(same(user), eq(checking.getId()), eq(savings.getAccountNumber()),
                eq(new BigDecimal("5000.00")), eq("Demo transfer to savings"),
                startsWith("demo-seed-" + user.getId()));
        verify(ledger).withdraw(same(user), eq(checking.getId()), eq(new BigDecimal("750.00")),
                eq("Demo cash withdrawal"), startsWith("demo-seed-" + user.getId()));
    }

    @Test
    void doesNotSeedWhenDemoCredentialsAreNotConfigured() {
        AppUserRepository users = mock(AppUserRepository.class);
        AccountService accountService = mock(AccountService.class);
        LedgerService ledger = mock(LedgerService.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

        new DemoDataBootstrap(users, accountService, ledger, passwordEncoder, "", "")
                .run(new DefaultApplicationArguments());

        verify(users, never()).save(any(AppUser.class));
        verify(accountService, never()).createFor(any(AppUser.class), eq("USD"));
        verify(ledger, never()).deposit(any(AppUser.class), any(), any(), any(), any());
    }

    @Test
    void rejectsIncompleteDemoCredentials() {
        DemoDataBootstrap bootstrap = new DemoDataBootstrap(
                mock(AppUserRepository.class), mock(AccountService.class), mock(LedgerService.class),
                mock(PasswordEncoder.class), "demo@example.test", "");

        assertThatThrownBy(() -> bootstrap.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LEDGER_DEMO_EMAIL and LEDGER_DEMO_PASSWORD");
    }
}
