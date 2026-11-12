package com.ledgerline.account;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class AccountDtos {
    private AccountDtos() {
    }

    public record CreateAccountRequest(
            @Pattern(regexp = "USD", message = "Only USD accounts are currently supported") String currency) {
        public String currencyOrDefault() {
            return currency == null ? "USD" : currency;
        }
    }

    public record AdminCreateAccountRequest(
            @NotBlank @Email @Size(max = 320) String email,
            @Pattern(regexp = "USD", message = "Only USD accounts are currently supported") String currency) {
        public String currencyOrDefault() {
            return currency == null ? "USD" : currency;
        }
    }

    public record AccountResponse(UUID id, String accountNumber, String currency, BigDecimal balance,
                                  Instant createdAt) {
        public static AccountResponse from(BankAccount account) {
            return new AccountResponse(account.getId(), account.getAccountNumber(), account.getCurrency(),
                    account.getBalance(), account.getCreatedAt());
        }
    }
}
