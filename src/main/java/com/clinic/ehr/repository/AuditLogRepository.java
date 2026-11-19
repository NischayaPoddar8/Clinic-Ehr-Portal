package com.clinic.ehr.repository;

import com.clinic.ehr.model.AuditLog;
import com.clinic.ehr.model.enums.AuditEntityType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @EntityGraph(attributePaths = "actor")
    List<AuditLog> findByActorIdOrderByCreatedAtDesc(Long actorId);

    @EntityGraph(attributePaths = "actor")
    List<AuditLog> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(AuditEntityType entityType, Long entityId);

    /** Admin audit viewer (paged, newest first). */
    @EntityGraph(attributePaths = "actor")
    List<AuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
