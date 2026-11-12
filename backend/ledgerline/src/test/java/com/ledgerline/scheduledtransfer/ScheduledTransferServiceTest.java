package com.ledgerline.scheduledtransfer;

import com.ledgerline.account.AccountService;
import com.ledgerline.account.BankAccount;
import com.ledgerline.account.BankAccountRepository;
import com.ledgerline.audit.AuditEventRepository;
import com.ledgerline.ledger.LedgerEntryRepository;
import com.ledgerline.ledger.LedgerService;
import com.ledgerline.ledger.LedgerTransactionRepository;
import com.ledgerline.user.AppUser;
import com.ledgerline.user.AppUserRepository;
import com.ledgerline.user.UserRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ScheduledTransferServiceTest {
    @Autowired private ScheduledTransferService scheduledTransfers;
    @Autowired private ScheduledTransferExecutionService execution;
    @Autowired private ScheduledTransferRepository schedules;
    @Autowired private LedgerService ledger;
    @Autowired private AccountService accountService;
    @Autowired private AppUserRepository users;
    @Autowired private BankAccountRepository accounts;
    @Autowired private LedgerTransactionRepository transactions;
    @Autowired private LedgerEntryRepository entries;
    @Autowired private AuditEventRepository auditEvents;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @BeforeEach
    void clearDatabase() {
        schedules.deleteAll();
        auditEvents.deleteAll();
        entries.deleteAll();
        transactions.deleteAll();
        accounts.deleteAll();
        users.deleteAll();
    }

    @Test
    void createsListsAndCancelsOnlyOwnersPendingTransfer() {
        AppUser owner = user("owner");
        AppUser outsider = user("outsider");
        BankAccount source = accountService.createFor(owner, "USD");
        BankAccount destination = accountService.createFor(outsider, "USD");
        var request = new ScheduledTransferDtos.CreateRequest(source.getId(), destination.getId(),
                new BigDecimal("12.34"), Instant.now().plusSeconds(3600));

        assertThatThrownBy(() -> scheduledTransfers.create(outsider, request))
                .isInstanceOf(com.ledgerline.common.ApiExceptions.Forbidden.class);
        var created = scheduledTransfers.create(owner, request);

        assertThat(scheduledTransfers.list(owner)).extracting(ScheduledTransferDtos.Response::id)
                .containsExactly(created.id());
        assertThat(scheduledTransfers.list(outsider)).isEmpty();
        assertThatThrownBy(() -> scheduledTransfers.cancel(created.id(), outsider))
                .isInstanceOf(com.ledgerline.common.ApiExceptions.Forbidden.class);

        var cancelled = scheduledTransfers.cancel(created.id(), owner);
        assertThat(cancelled.status()).isEqualTo(ScheduledTransferStatus.CANCELLED);
        assertThatThrownBy(() -> scheduledTransfers.cancel(created.id(), owner))
                .isInstanceOf(com.ledgerline.common.ApiExceptions.Conflict.class);
    }

    @Test
    void dueTransfersUseLedgerAndRecordFailedTransfersWithoutPartialPosting() {
        AppUser owner = user("scheduler");
        AppUser recipient = user("recipient");
        BankAccount source = accountService.createFor(owner, "USD");
        BankAccount destination = accountService.createFor(recipient, "USD");
        ledger.deposit(owner, source.getId(), new BigDecimal("20.00"), null, "scheduled-seed");

        var successful = scheduledTransfers.create(owner, request(source, destination, "7.25"));
        makeDue(successful.id());
        execution.dueTransferIds(Instant.now()).forEach(execution::execute);

        assertThat(schedules.findById(successful.id()).orElseThrow().getStatus())
                .isEqualTo(ScheduledTransferStatus.COMPLETED);
        assertThat(accounts.findById(source.getId()).orElseThrow().getBalance()).isEqualByComparingTo("12.75");
        assertThat(accounts.findById(destination.getId()).orElseThrow().getBalance()).isEqualByComparingTo("7.25");
        assertThat(transactions.count()).isEqualTo(2);
        assertThat(entries.count()).isEqualTo(4);

        var failed = scheduledTransfers.create(owner, request(source, destination, "100.00"));
        makeDue(failed.id());
        execution.dueTransferIds(Instant.now()).forEach(execution::execute);

        ScheduledTransfer failedRecord = schedules.findById(failed.id()).orElseThrow();
        assertThat(failedRecord.getStatus()).isEqualTo(ScheduledTransferStatus.FAILED);
        assertThat(failedRecord.getFailureReason()).contains("Insufficient funds");
        assertThat(accounts.findById(source.getId()).orElseThrow().getBalance()).isEqualByComparingTo("12.75");
        assertThat(accounts.findById(destination.getId()).orElseThrow().getBalance()).isEqualByComparingTo("7.25");
        assertThat(transactions.count()).isEqualTo(2);
        assertThat(entries.count()).isEqualTo(4);
    }

    @Test
    void authenticatedEndpointsEnforceCustomerOwnershipAndAllowCancellation() throws Exception {
        String ownerToken = register("api-owner").get("token").asText();
        String outsiderToken = register("api-outsider").get("token").asText();
        AppUser outsider = users.findByEmailIgnoreCase("api-outsider@example.test").orElseThrow();
        BankAccount destination = accountService.createFor(outsider, "USD");
        BankAccount source = accounts.findByOwnerIdOrderByCreatedAtAsc(
                users.findByEmailIgnoreCase("api-owner@example.test").orElseThrow().getId()).get(0);

        String response = mockMvc.perform(post("/api/scheduled-transfers")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ScheduledTransferDtos.CreateRequest(
                                source.getId(), destination.getId(), new BigDecimal("5.00"),
                                Instant.now().plusSeconds(3600)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        String scheduleId = objectMapper.readTree(response).get("id").asText();

        mockMvc.perform(post("/api/scheduled-transfers/{id}/cancel", scheduleId)
                        .header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/scheduled-transfers/{id}/cancel", scheduleId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    private com.fasterxml.jackson.databind.JsonNode register(String prefix) throws Exception {
        String body = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + prefix + "@example.test\","
                                + "\"password\":\"correct-horse-battery\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private AppUser user(String prefix) {
        return users.saveAndFlush(new AppUser(prefix + "@example.test", "hash", UserRole.CUSTOMER));
    }

    private ScheduledTransferDtos.CreateRequest request(BankAccount source, BankAccount destination,
                                                         String amount) {
        return new ScheduledTransferDtos.CreateRequest(source.getId(), destination.getId(),
                new BigDecimal(amount), Instant.now().plusSeconds(3600));
    }

    private void makeDue(java.util.UUID id) {
        jdbc.update("UPDATE scheduled_transfers SET scheduled_at = ? WHERE id = ?",
                Timestamp.from(Instant.now().minusSeconds(1)), id);
    }
}
