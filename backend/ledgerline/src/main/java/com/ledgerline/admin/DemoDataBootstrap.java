package com.ledgerline.admin;

import com.ledgerline.account.AccountService;
import com.ledgerline.account.BankAccount;
import com.ledgerline.ledger.LedgerService;
import com.ledgerline.user.AppUser;
import com.ledgerline.user.AppUserRepository;
import com.ledgerline.user.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Locale;

@Component
public class DemoDataBootstrap implements ApplicationRunner {
    private static final Logger logger = LoggerFactory.getLogger(DemoDataBootstrap.class);

    private final AppUserRepository users;
    private final AccountService accountService;
    private final LedgerService ledger;
    private final PasswordEncoder passwordEncoder;
    private final String demoEmail;
    private final String demoPassword;

    public DemoDataBootstrap(AppUserRepository users, AccountService accountService,
            LedgerService ledger, PasswordEncoder passwordEncoder,
            @Value("${ledgerline.bootstrap.demo-email:}") String demoEmail,
            @Value("${ledgerline.bootstrap.demo-password:}") String demoPassword) {
        this.users = users;
        this.accountService = accountService;
        this.ledger = ledger;
        this.passwordEncoder = passwordEncoder;
        this.demoEmail = demoEmail;
        this.demoPassword = demoPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (demoEmail.isBlank() && demoPassword.isBlank()) {
            return;
        }
        if (demoEmail.isBlank() || demoPassword.length() < 12 || demoPassword.length() > 72) {
            throw new IllegalStateException(
                    "LEDGER_DEMO_EMAIL and LEDGER_DEMO_PASSWORD must both be set; password must contain 12 to 72 characters");
        }

        String normalizedEmail = demoEmail.trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCase(normalizedEmail)) {
            logger.info("Demo data was not created because {} already exists. Use a new demo email to seed fresh data.",
                    normalizedEmail);
            return;
        }

        AppUser demoUser = users.save(new AppUser(normalizedEmail,
                passwordEncoder.encode(demoPassword), UserRole.CUSTOMER));
        BankAccount checking = accountService.createFor(demoUser, "USD");
        BankAccount savings = accountService.createFor(demoUser, "USD");
        String keyPrefix = "demo-seed-" + demoUser.getId() + "-";

        ledger.deposit(demoUser, checking.getId(), new BigDecimal("45000.00"),
                "Demo salary credit", keyPrefix + "salary");
        ledger.deposit(demoUser, checking.getId(), new BigDecimal("1200.00"),
                "Demo refund", keyPrefix + "refund");
        ledger.transfer(demoUser, checking.getId(), savings.getAccountNumber(),
                new BigDecimal("5000.00"), "Demo transfer to savings", keyPrefix + "transfer");
        ledger.withdraw(demoUser, checking.getId(), new BigDecimal("750.00"),
                "Demo cash withdrawal", keyPrefix + "withdrawal");

        logger.info("Created a demo customer with two USD accounts and four clearly labeled demo transactions.");
    }
}
