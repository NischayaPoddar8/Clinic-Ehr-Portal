package com.clinic.ehr.dto;

import com.clinic.ehr.model.AuditLog;
import com.clinic.ehr.model.enums.AuditAction;
import com.clinic.ehr.model.enums.AuditEntityType;
import java.time.LocalDateTime;

public record AuditLogResponse(Long id, Long actorId, String actorEmail, String actorName,
                               AuditAction action, AuditEntityType entityType, Long entityId,
                               String details, LocalDateTime createdAt) {

    public static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(log.getId(), log.getActor().getId(), log.getActor().getEmail(),
                log.getActor().getDisplayName(), log.getAction(), log.getEntityType(), log.getEntityId(),
                log.getDetails(), log.getCreatedAt());
    }
}
