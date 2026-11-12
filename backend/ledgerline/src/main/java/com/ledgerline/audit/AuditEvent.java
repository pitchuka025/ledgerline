package com.ledgerline.audit;

import com.ledgerline.user.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_events")
public class AuditEvent {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private AppUser actor;

    @Column(nullable = false, length = 50)
    private String action;

    @Column(name = "resource_type", nullable = false, length = 50)
    private String resourceType;

    @Column(name = "resource_id", length = 64)
    private String resourceId;

    @Column(nullable = false, length = 500)
    private String details;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AuditEvent() {
    }

    public AuditEvent(AppUser actor, String action, String resourceType, String resourceId, String details) {
        this.id = UUID.randomUUID();
        this.actor = actor;
        this.action = action;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.details = details;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getAction() { return action; }
    public String getResourceType() { return resourceType; }
    public String getResourceId() { return resourceId; }
    public String getDetails() { return details; }
    public Instant getCreatedAt() { return createdAt; }
}
