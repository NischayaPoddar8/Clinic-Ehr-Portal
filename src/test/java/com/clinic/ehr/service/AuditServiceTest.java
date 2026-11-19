package com.clinic.ehr.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.clinic.ehr.dto.AuditLogResponse;
import com.clinic.ehr.model.AuditLog;
import com.clinic.ehr.model.User;
import com.clinic.ehr.model.enums.AuditAction;
import com.clinic.ehr.model.enums.AuditEntityType;
import com.clinic.ehr.model.enums.Role;
import com.clinic.ehr.repository.AuditLogRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

class AuditServiceTest {

    private AuditLogRepository repository;
    private AuditService service;
    private User actor;

    @BeforeEach
    void setUp() {
        repository = mock(AuditLogRepository.class);
        service = new AuditService(repository);
        actor = new User("a@x.com", "h", "Alice", null, Role.ADMIN);
        ReflectionTestUtils.setField(actor, "id", 1L);
        when(repository.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void recordPersistsAllFields() {
        AuditLog saved = service.record(actor, AuditAction.CREATE, AuditEntityType.USER, 5L, "created");
        assertThat(saved.getActor()).isSameAs(actor);
        assertThat(saved.getAction()).isEqualTo(AuditAction.CREATE);
        assertThat(saved.getEntityType()).isEqualTo(AuditEntityType.USER);
        assertThat(saved.getEntityId()).isEqualTo(5L);
        assertThat(saved.getDetails()).isEqualTo("created");
    }

    @Test
    void overlongDetailsAreTruncated() {
        AuditLog saved = service.record(actor, AuditAction.UPDATE, AuditEntityType.APPOINTMENT, 1L, "x".repeat(900));
        assertThat(saved.getDetails()).hasSize(500);
    }

    @Test
    void recentClampsPageAndSize() {
        AuditLog entry = new AuditLog(actor, AuditAction.BOOK, AuditEntityType.APPOINTMENT, 7L, "booked");
        when(repository.findAllByOrderByCreatedAtDesc(any(Pageable.class))).thenReturn(List.of(entry));

        List<AuditLogResponse> result = service.recent(-3, 5000);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAllByOrderByCreatedAtDesc(pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).actorEmail()).isEqualTo("a@x.com");
        assertThat(result.get(0).action()).isEqualTo(AuditAction.BOOK);
    }
}
