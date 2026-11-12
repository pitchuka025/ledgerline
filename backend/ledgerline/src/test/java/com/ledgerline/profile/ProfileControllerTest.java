package com.ledgerline.profile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgerline.audit.AuditEventRepository;
import com.ledgerline.auth.JwtService;
import com.ledgerline.ledger.LedgerEntryRepository;
import com.ledgerline.ledger.LedgerTransactionRepository;
import com.ledgerline.account.BankAccountRepository;
import com.ledgerline.user.AppUser;
import com.ledgerline.user.AppUserRepository;
import com.ledgerline.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProfileControllerTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AppUserRepository users;
    @Autowired private UserPreferencesRepository preferences;
    @Autowired private BankAccountRepository accounts;
    @Autowired private LedgerTransactionRepository transactions;
    @Autowired private LedgerEntryRepository entries;
    @Autowired private AuditEventRepository auditEvents;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtService jwtService;

    @BeforeEach
    void clearDatabase() {
        preferences.deleteAll();
        auditEvents.deleteAll();
        entries.deleteAll();
        transactions.deleteAll();
        accounts.deleteAll();
        users.deleteAll();
    }

    @Test
    void customerCanSaveProfilePreferencesAndChangePassword() throws Exception {
        JsonNode registered = register("profile@example.test", "initial-private-password");
        String token = registered.get("token").asText();

        mockMvc.perform(get("/api/profile").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("profile"));

        mockMvc.perform(put("/api/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"new-profile@example.test","displayName":"New Profile"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("new-profile@example.test"))
                .andExpect(jsonPath("$.displayName").value("New Profile"));

        mockMvc.perform(put("/api/profile/preferences")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginAlertsEnabled":false,"emailNotificationsEnabled":true,"hideBalances":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loginAlertsEnabled").value(false))
                .andExpect(jsonPath("$.hideBalances").value(true));

        mockMvc.perform(post("/api/profile/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"initial-private-password","newPassword":"replacement-private-password"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"new-profile@example.test","password":"replacement-private-password"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void administratorCanCreateAuditorsAndUpdateRolesButNotGrantAdminRole() throws Exception {
        AppUser admin = users.saveAndFlush(new AppUser("admin@example.test",
                passwordEncoder.encode("admin-private-password"), UserRole.ADMIN));
        String token = jwtService.issue(admin);

        String createdBody = mockMvc.perform(post("/api/admin/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"auditor@example.test","displayName":"Auditor","role":"AUDITOR","password":"auditor-private-password"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("AUDITOR"))
                .andReturn().getResponse().getContentAsString();
        JsonNode created = objectMapper.readTree(createdBody);

        mockMvc.perform(patch("/api/admin/users/{id}/role", created.get("id").asText())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"CUSTOMER"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("CUSTOMER"));

        mockMvc.perform(patch("/api/admin/users/{id}/role", admin.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"CUSTOMER"}
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").exists());
    }

    private JsonNode register(String email, String password) throws Exception {
        String body = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }
}
