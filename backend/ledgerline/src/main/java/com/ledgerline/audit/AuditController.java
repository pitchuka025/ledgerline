package com.ledgerline.audit;

import com.ledgerline.common.ApiExceptions;
import com.ledgerline.user.AppUser;
import com.ledgerline.user.UserRole;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/audit")
public class AuditController {
    private final AuditEventRepository events;

    public AuditController(AuditEventRepository events) {
        this.events = events;
    }

    @GetMapping
    public List<AuditEventResponse> recent(@AuthenticationPrincipal AppUser user) {
        if (user.getRole() == UserRole.CUSTOMER) {
            throw new ApiExceptions.Forbidden("Auditor or administrator access is required");
        }
        return events.findTop100ByOrderByCreatedAtDesc().stream()
                .map(event -> new AuditEventResponse(event.getId(), event.getAction(), event.getResourceType(),
                        event.getResourceId(), event.getDetails(), event.getCreatedAt()))
                .toList();
    }

    public record AuditEventResponse(UUID id, String action, String resourceType, String resourceId,
                                     String details, Instant createdAt) {
    }
}
