package com.clinic.ehr.service;

import static com.clinic.ehr.support.TestData.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.clinic.ehr.dto.UserResponse;
import com.clinic.ehr.exception.InvalidBookingException;
import com.clinic.ehr.exception.ResourceNotFoundException;
import com.clinic.ehr.model.User;
import com.clinic.ehr.model.enums.AuditAction;
import com.clinic.ehr.model.enums.AuditEntityType;
import com.clinic.ehr.model.enums.Role;
import com.clinic.ehr.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class UserServiceTest {

    private UserRepository userRepository;
    private AuditService auditService;
    private UserService userService;
    private User admin;
    private User patientUser;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        auditService = mock(AuditService.class);
        userService = new UserService(userRepository, auditService);

        admin = withId(new User("admin@test.local", "hash", "Admin", null, Role.ADMIN), 1L);
        patientUser = withId(new User("patient@test.local", "hash", "Patient", null, Role.PATIENT), 2L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(userRepository.findById(2L)).thenReturn(Optional.of(patientUser));
    }

    @Test
    void listUsersReturnsFilteredUsers() {
        when(userRepository.findFiltered(Role.PATIENT, true, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(patientUser)));

        List<UserResponse> list = userService.listUsers(Role.PATIENT, true, 0, 20);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).email()).isEqualTo("patient@test.local");
    }

    @Test
    void updateStatusDeactivatesUserAndAudits() {
        UserResponse response = userService.updateStatus(2L, false, 1L);
        assertThat(patientUser.isActive()).isFalse();
        assertThat(response.active()).isFalse();
        verify(auditService).record(same(admin), eq(AuditAction.UPDATE), eq(AuditEntityType.USER), eq(2L), anyString());
    }

    @Test
    void adminCannotDeactivateSelf() {
        assertThatThrownBy(() -> userService.updateStatus(1L, false, 1L))
                .isInstanceOf(InvalidBookingException.class);
        assertThat(admin.isActive()).isTrue();
        verify(auditService, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void adminCanReactivateSelf() {
        admin.setActive(false);
        UserResponse response = userService.updateStatus(1L, true, 1L);
        assertThat(admin.isActive()).isTrue();
        assertThat(response.active()).isTrue();
        verify(auditService).record(same(admin), eq(AuditAction.UPDATE), eq(AuditEntityType.USER), eq(1L), anyString());
    }

    @Test
    void updateStatusRejectsUnknownUser() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> userService.updateStatus(999L, false, 1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
