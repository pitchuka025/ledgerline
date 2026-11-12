package com.ledgerline.auth;

import com.ledgerline.account.AccountService;
import com.ledgerline.audit.AuditEvent;
import com.ledgerline.audit.AuditEventRepository;
import com.ledgerline.common.ApiExceptions;
import com.ledgerline.user.AppUser;
import com.ledgerline.user.AppUserRepository;
import com.ledgerline.user.UserRole;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AuthService {
    private final AppUserRepository users;
    private final AccountService accounts;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditEventRepository auditEvents;
    private final LoginAttemptGuard loginAttemptGuard;

    public AuthService(AppUserRepository users, AccountService accounts, PasswordEncoder passwordEncoder,
                       JwtService jwtService, AuditEventRepository auditEvents,
                       LoginAttemptGuard loginAttemptGuard) {
        this.users = users;
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.auditEvents = auditEvents;
        this.loginAttemptGuard = loginAttemptGuard;
    }

    @Transactional
    public AuthDtos.AuthResponse register(AuthDtos.RegisterRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ApiExceptions.Conflict("An account with this email already exists");
        }
        AppUser user = users.save(new AppUser(email, passwordEncoder.encode(request.password()), UserRole.CUSTOMER));
        accounts.createFor(user, "USD");
        auditEvents.save(new AuditEvent(user, "USER_REGISTERED", "USER", user.getId().toString(),
                "Customer registered"));
        return response(user);
    }

    @Transactional(readOnly = true)
    public AuthDtos.AuthResponse login(AuthDtos.LoginRequest request, String clientAddress) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        loginAttemptGuard.checkAllowed(email, clientAddress);
        AppUser user = users.findByEmailIgnoreCase(email)
                .filter(candidate -> passwordEncoder.matches(request.password(), candidate.getPasswordHash()))
                .orElse(null);
        if (user == null) {
            loginAttemptGuard.recordFailure(email, clientAddress);
            throw new BadCredentialsException("Invalid email or password");
        }
        loginAttemptGuard.clear(email, clientAddress);
        return response(user);
    }

    private AuthDtos.AuthResponse response(AppUser user) {
        return new AuthDtos.AuthResponse(jwtService.issue(user), user.getId(), user.getEmail(), user.getRole());
    }
}
