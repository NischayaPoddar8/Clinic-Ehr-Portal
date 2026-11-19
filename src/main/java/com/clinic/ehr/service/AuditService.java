package com.clinic.ehr.service;

import com.clinic.ehr.dto.AuditLogResponse;
import com.clinic.ehr.model.AuditLog;
import com.clinic.ehr.model.User;
import com.clinic.ehr.model.enums.AuditAction;
import com.clinic.ehr.model.enums.AuditEntityType;
import com.clinic.ehr.repository.AuditLogRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Writes and reads the audit trail. Writes join the caller's transaction, so a change and its audit row commit together. */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private static final int MAX_PAGE_SIZE = 100;

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public AuditLog record(User actor, AuditAction action, AuditEntityType entityType, Long entityId, String details) {
        AuditLog saved = auditLogRepository.save(new AuditLog(actor, action, entityType, entityId, details));
        log.info("AUDIT actor={} action={} entity={}#{}", actor.getId(), action, entityType, entityId);
        return saved;
    }

    /** Newest entries first. Page size is clamped to 1..100. */
    @Transactional(readOnly = true)
    public List<AuditLogResponse> recent(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return auditLogRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(safePage, safeSize))
                .stream().map(AuditLogResponse::from).toList();
    }
}
