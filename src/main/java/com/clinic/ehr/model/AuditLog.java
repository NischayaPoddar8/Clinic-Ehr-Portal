package com.clinic.ehr.model;

import com.clinic.ehr.model.enums.AuditAction;
import com.clinic.ehr.model.enums.AuditEntityType;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;

/** Immutable record of an important change. No setters; all columns are insert-only. */
@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_audit_entity", columnList = "entity_type,entity_id"),
        @Index(name = "idx_audit_actor_created", columnList = "actor_id,created_at"),
        @Index(name = "idx_audit_created", columnList = "created_at")
})
public class AuditLog {

    private static final int MAX_DETAILS = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id", nullable = false, updatable = false)
    private User actor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 15)
    private AuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, updatable = false, length = 20)
    private AuditEntityType entityType;

    @Column(name = "entity_id", nullable = false, updatable = false)
    private Long entityId;

    @Column(length = MAX_DETAILS, updatable = false)
    private String details;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected AuditLog() { }

    public AuditLog(User actor, AuditAction action, AuditEntityType entityType, Long entityId, String details) {
        this.actor = Objects.requireNonNull(actor, "actor");
        this.action = Objects.requireNonNull(action, "action");
        this.entityType = Objects.requireNonNull(entityType, "entityType");
        this.entityId = Objects.requireNonNull(entityId, "entityId");
        this.details = (details != null && details.length() > MAX_DETAILS) ? details.substring(0, MAX_DETAILS) : details;
    }

    public Long getId() { return id; }
    public User getActor() { return actor; }
    public AuditAction getAction() { return action; }
    public AuditEntityType getEntityType() { return entityType; }
    public Long getEntityId() { return entityId; }
    public String getDetails() { return details; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
