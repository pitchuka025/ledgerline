package com.ledgerline.ledger;

import com.ledgerline.account.AccountService;
import com.ledgerline.account.BankAccount;
import com.ledgerline.account.BankAccountRepository;
import com.ledgerline.audit.AuditEventRepository;
import com.ledgerline.common.ApiExceptions;
import com.ledgerline.user.AppUser;
import com.ledgerline.user.AppUserRepository;
import com.ledgerline.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LedgerServiceTest {
    @Autowired private LedgerService ledger;
    @Autowired private AccountService accountService;
    @Autowired private AppUserRepository users;
    @Autowired private BankAccountRepository accounts;
    @Autowired private LedgerTransactionRepository transactions;
    @Autowired private LedgerEntryRepository entries;
    @Autowired private AuditEventRepository auditEvents;
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @BeforeEach
    void clearDatabase() {
        auditEvents.deleteAll();
        entries.deleteAll();
        transactions.deleteAll();
        accounts.deleteAll();
        users.deleteAll();
    }

    @Test
    void transfersBalanceAndIdenticalRetriesDoNotPostTwice() {
        AppUser sender = users.saveAndFlush(new AppUser("sender@example.test", "hash", UserRole.CUSTOMER));
        AppUser receiver = users.saveAndFlush(new AppUser("receiver@example.test", "hash", UserRole.CUSTOMER));
        BankAccount senderAccount = accountService.createFor(sender, "USD");
        BankAccount receiverAccount = accountService.createFor(receiver, "USD");
        ledger.deposit(sender, senderAccount.getId(), new BigDecimal("100.00"), "opening funds", "deposit-1");

        TransactionDtos.TransactionResponse first = ledger.transfer(sender, senderAccount.getId(),
                receiverAccount.getAccountNumber(), new BigDecimal("35.25"), "invoice", "transfer-1");
        TransactionDtos.TransactionResponse retry = ledger.transfer(sender, senderAccount.getId(),
                receiverAccount.getAccountNumber(), new BigDecimal("35.25"), "invoice", "transfer-1");

        assertThat(retry.id()).isEqualTo(first.id());
        assertThat(accounts.findById(senderAccount.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo("64.75");
        assertThat(accounts.findById(receiverAccount.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo("35.25");
        assertThat(transactions.count()).isEqualTo(2);

        var transferEntries = entries.findByTransactionIdOrderByCreatedAtAsc(first.id());
        assertThat(transferEntries).hasSize(2);
        assertThat(transferEntries.stream().filter(entry -> entry.getEntryType() == EntryType.DEBIT)
                .map(LedgerEntry::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("35.25");
        assertThat(transferEntries.stream().filter(entry -> entry.getEntryType() == EntryType.CREDIT)
                .map(LedgerEntry::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("35.25");
        TransactionDtos.LedgerProofResponse proof = ledger.proof(first.reference(), sender);
        assertThat(proof.balanced()).isTrue();
        assertThat(proof.totalDebits()).isEqualByComparingTo(proof.totalCredits());
        assertThat(proof.entries()).hasSize(2);
        assertThat(auditEvents.count()).isEqualTo(4);
        AppUser stranger = users.saveAndFlush(new AppUser("stranger@example.test", "hash", UserRole.CUSTOMER));
        assertThatThrownBy(() -> ledger.proof(first.reference(), stranger))
                .isInstanceOf(ApiExceptions.Forbidden.class);
    }

    @Test
    void rejectsNegativeBalancesInvalidPrecisionAndKeyReuseForDifferentRequests() {
        AppUser owner = users.saveAndFlush(new AppUser("owner@example.test", "hash", UserRole.CUSTOMER));
        BankAccount account = accountService.createFor(owner, "USD");

        assertThatThrownBy(() -> ledger.withdraw(owner, account.getId(), new BigDecimal("0.001"),
                null, "fractional"))
                .isInstanceOf(IllegalArgumentException.class);
        ledger.deposit(owner, account.getId(), new BigDecimal("10.00"), null, "same-key");
        assertThatThrownBy(() -> ledger.withdraw(owner, account.getId(), new BigDecimal("11.00"),
                null, "overdraw"))
                .isInstanceOf(ApiExceptions.Conflict.class);
        assertThatThrownBy(() -> ledger.deposit(owner, account.getId(), new BigDecimal("20.00"),
                null, "same-key"))
                .isInstanceOf(ApiExceptions.Conflict.class);

        assertThat(accounts.findById(account.getId()).orElseThrow().getBalance()).isEqualByComparingTo("10.00");
        assertThat(transactions.count()).isEqualTo(1);
        assertThat(entries.count()).isEqualTo(2);
    }

    @Test
    void simultaneousWithdrawalsCannotOverdrawAnAccount() throws Exception {
        AppUser owner = users.saveAndFlush(new AppUser("concurrent@example.test", "hash", UserRole.CUSTOMER));
        BankAccount account = accountService.createFor(owner, "USD");
        ledger.deposit(owner, account.getId(), new BigDecimal("100.00"), null, "concurrent-seed");

        var executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Boolean> first = executor.submit(() -> tryWithdrawal(start, owner, account, "withdraw-1"));
            Future<Boolean> second = executor.submit(() -> tryWithdrawal(start, owner, account, "withdraw-2"));
            start.countDown();

            assertThat((int) (first.get() ? 1 : 0) + (second.get() ? 1 : 0)).isEqualTo(1);
            assertThat(accounts.findById(account.getId()).orElseThrow().getBalance())
                    .isEqualByComparingTo("30.00");
            assertThat(transactions.count()).isEqualTo(2);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void simultaneousRetriesWithTheSameKeyReturnOneTransaction() throws Exception {
        AppUser owner = users.saveAndFlush(new AppUser("retry@example.test", "hash", UserRole.CUSTOMER));
        BankAccount account = accountService.createFor(owner, "USD");
        ledger.deposit(owner, account.getId(), new BigDecimal("100.00"), null, "retry-seed");

        var executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<TransactionDtos.TransactionResponse> first = executor.submit(() ->
                    withdrawAfter(start, owner, account, "same-withdrawal"));
            Future<TransactionDtos.TransactionResponse> second = executor.submit(() ->
                    withdrawAfter(start, owner, account, "same-withdrawal"));
            start.countDown();

            assertThat(first.get().id()).isEqualTo(second.get().id());
            assertThat(accounts.findById(account.getId()).orElseThrow().getBalance())
                    .isEqualByComparingTo("30.00");
            assertThat(transactions.count()).isEqualTo(2);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void transferAcceptsLowercaseAccountNumbers() {
        AppUser sender = users.saveAndFlush(new AppUser("sender_lower@example.test", "hash", UserRole.CUSTOMER));
        AppUser receiver = users.saveAndFlush(new AppUser("receiver_lower@example.test", "hash", UserRole.CUSTOMER));
        BankAccount senderAccount = accountService.createFor(sender, "USD");
        BankAccount receiverAccount = accountService.createFor(receiver, "USD");
        ledger.deposit(sender, senderAccount.getId(), new BigDecimal("100.00"), "opening funds", "lowercase-deposit");

        TransactionDtos.TransactionResponse transfer = ledger.transfer(sender, senderAccount.getId(),
                receiverAccount.getAccountNumber().toLowerCase(), new BigDecimal("25.00"), "invoice",
                "lowercase-transfer");

        assertThat(transfer.amount()).isEqualByComparingTo("25.00");
        assertThat(accounts.findById(senderAccount.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo("75.00");
        assertThat(accounts.findById(receiverAccount.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo("25.00");
    }

    @Test
    void customerCanRegisterDepositAndInspectTheLedgerProofOverHttp() throws Exception {
        String registration = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"http-customer@example.test","password":"correct-horse-battery"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(registration).get("token").asText();

        String accountList = mockMvc.perform(get("/api/accounts").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var account = objectMapper.readTree(accountList).get(0);
        String accountId = account.get("id").asText();

        String movement = mockMvc.perform(post("/api/accounts/{id}/deposit", accountId)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "http-deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":25.50,"description":"Browser smoke test"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("DEPOSIT"))
                .andReturn().getResponse().getContentAsString();
        String reference = objectMapper.readTree(movement).get("reference").asText();

        mockMvc.perform(get("/api/transactions/{reference}/proof", reference)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balanced").value(true))
                .andExpect(jsonPath("$.totalDebits").value(25.50))
                .andExpect(jsonPath("$.totalCredits").value(25.50))
                .andExpect(jsonPath("$.entries.length()").value(2));

        mockMvc.perform(post("/api/admin/accounts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"another-customer@example.test","currency":"USD"}
                                """))
                .andExpect(status().isForbidden());
    }

    private boolean tryWithdrawal(CountDownLatch start, AppUser owner, BankAccount account, String key)
            throws InterruptedException {
        start.await();
        try {
            ledger.withdraw(owner, account.getId(), new BigDecimal("70.00"), null, key);
            return true;
        } catch (ApiExceptions.Conflict exception) {
            return false;
        }
    }

    private TransactionDtos.TransactionResponse withdrawAfter(CountDownLatch start, AppUser owner,
                                                                BankAccount account, String key)
            throws InterruptedException {
        start.await();
        return ledger.withdraw(owner, account.getId(), new BigDecimal("70.00"), null, key);
    }
}
