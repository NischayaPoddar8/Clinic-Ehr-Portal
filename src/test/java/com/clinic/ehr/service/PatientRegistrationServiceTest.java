package com.clinic.ehr.service;

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

import com.clinic.ehr.dto.RegisterRequest;
import com.clinic.ehr.dto.UserResponse;
import com.clinic.ehr.exception.DuplicateResourceException;
import com.clinic.ehr.model.Patient;
import com.clinic.ehr.model.User;
import com.clinic.ehr.model.enums.AuditAction;
import com.clinic.ehr.model.enums.AuditEntityType;
import com.clinic.ehr.model.enums.Gender;
import com.clinic.ehr.model.enums.Role;
import com.clinic.ehr.repository.UserRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class PatientRegistrationServiceTest {

    private UserRepository userRepository;
    private PasswordEncoder encoder;
    private AuditService auditService;
    private PatientRegistrationService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        encoder = mock(PasswordEncoder.class);
        auditService = mock(AuditService.class);
        service = new PatientRegistrationService(userRepository, encoder, auditService);
        when(encoder.encode("Passw0rd1")).thenReturn("HASHED");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 10L);
            return saved;
        });
    }

    private RegisterRequest request(String email) {
        return new RegisterRequest("Nina Patient", email, "Passw0rd1", "9876543210",
                LocalDate.of(1995, 3, 4), Gender.FEMALE, "Street 1", "O+", "Mother 111");
    }

    @Test
    void createsPatientWithHashedPasswordNormalizedEmailAndAudit() {
        UserResponse response = service.create(request("  New.Patient@Example.COM "), null);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue()).isInstanceOf(Patient.class);
        assertThat(saved.getValue().getEmail()).isEqualTo("new.patient@example.com");
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("HASHED").isNotEqualTo("Passw0rd1");
        assertThat(saved.getValue().getRole()).isEqualTo(Role.PATIENT);
        assertThat(((Patient) saved.getValue()).getBloodGroup()).isEqualTo("O+");

        // self-registration: the new account is its own audit actor
        verify(auditService).record(same(saved.getValue()), eq(AuditAction.CREATE),
                eq(AuditEntityType.USER), eq(10L), anyString());
        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.role()).isEqualTo(Role.PATIENT);
    }

    @Test
    void duplicateEmailIsRejectedBeforeAnythingIsSaved() {
        when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request("Taken@Example.com"), null))
                .isInstanceOf(DuplicateResourceException.class);
        verify(userRepository, never()).save(any(User.class));
        verify(auditService, never()).record(any(), any(), any(), any(), any());
    }
}
