package com.ledgerline.admin;

import com.ledgerline.user.AppUser;
import com.ledgerline.user.AppUserRepository;
import com.ledgerline.user.UserRole;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Component
public class BootstrapUsers implements ApplicationRunner {
    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminPassword;
    private final String auditorEmail;
    private final String auditorPassword;

    public BootstrapUsers(AppUserRepository users, PasswordEncoder passwordEncoder,
            @Value("${ledgerline.bootstrap.admin-email}") String adminEmail,
            @Value("${ledgerline.bootstrap.admin-password}") String adminPassword,
            @Value("${ledgerline.bootstrap.auditor-email}") String auditorEmail,
            @Value("${ledgerline.bootstrap.auditor-password}") String auditorPassword) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
        this.auditorEmail = auditorEmail;
        this.auditorPassword = auditorPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        bootstrap(adminEmail, adminPassword, UserRole.ADMIN, "LEDGER_ADMIN");
        bootstrap(auditorEmail, auditorPassword, UserRole.AUDITOR, "LEDGER_AUDITOR");
    }

    private void bootstrap(String email, String password, UserRole role, String variablePrefix) {
        if (email.isBlank() && password.isBlank()) {
            return;
        }
        if (email.isBlank() || password.isBlank() || password.length() < 12 || password.length() > 72) {
            throw new IllegalStateException(variablePrefix + "_EMAIL and " + variablePrefix
                    + "_PASSWORD must both be set; password must contain 12 to 72 characters");
        }
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        if (!users.existsByEmailIgnoreCase(normalizedEmail)) {
            users.save(new AppUser(normalizedEmail, passwordEncoder.encode(password), role));
        }
    }
}
