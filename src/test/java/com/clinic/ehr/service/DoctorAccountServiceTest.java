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

import com.clinic.ehr.dto.CreateDoctorRequest;
import com.clinic.ehr.dto.UserResponse;
import com.clinic.ehr.exception.DuplicateResourceException;
import com.clinic.ehr.exception.ResourceNotFoundException;
import com.clinic.ehr.model.Doctor;
import com.clinic.ehr.model.User;
import com.clinic.ehr.model.enums.AuditAction;
import com.clinic.ehr.model.enums.AuditEntityType;
import com.clinic.ehr.model.enums.Role;
import com.clinic.ehr.repository.DoctorRepository;
import com.clinic.ehr.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class DoctorAccountServiceTest {

    private UserRepository userRepository;
    private DoctorRepository doctorRepository;
    private AuditService auditService;
    private DoctorAccountService service;
    private User admin;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        doctorRepository = mock(DoctorRepository.class);
        auditService = mock(AuditService.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.encode(anyString())).thenReturn("HASHED");
        service = new DoctorAccountService(userRepository, doctorRepository, encoder, auditService);

        admin = new User("admin@x.com", "h", "Admin", null, Role.ADMIN);
        ReflectionTestUtils.setField(admin, "id", 1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 20L);
            return saved;
        });
    }

    private CreateDoctorRequest request(String license, Integer slotMinutes) {
        return new CreateDoctorRequest("Dan Doctor", "Dan@Clinic.com", "Passw0rd1", "123456789",
                "Cardiology", license, "MD", slotMinutes);
    }

    @Test
    void createsDoctorAuditedUnderTheAdmin() {
        UserResponse response = service.create(request("LIC-1", 20), 1L);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        Doctor doctor = (Doctor) saved.getValue();
        assertThat(doctor.getRole()).isEqualTo(Role.DOCTOR);
        assertThat(doctor.getSlotMinutes()).isEqualTo(20);
        assertThat(doctor.getEmail()).isEqualTo("dan@clinic.com");
        verify(auditService).record(same(admin), eq(AuditAction.CREATE), eq(AuditEntityType.USER), eq(20L),
                eq("Created DOCTOR account dan@clinic.com (Cardiology)")); // overridden auditDetails
        assertThat(response.displayName()).isEqualTo("Dr. Dan Doctor");
    }

    @Test
    void slotLengthDefaultsWhenNotGiven() {
        service.create(request("LIC-2", null), 1L);
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(((Doctor) saved.getValue()).getSlotMinutes()).isEqualTo(30);
    }

    @Test
    void duplicateLicenseIsRejected() {
        when(doctorRepository.existsByLicenseNumber("LIC-1")).thenReturn(true);
        assertThatThrownBy(() -> service.create(request("LIC-1", null), 1L))
                .isInstanceOf(DuplicateResourceException.class);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void unknownActorIsRejected() {
        assertThatThrownBy(() -> service.create(request("LIC-3", null), 99L))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(userRepository, never()).save(any(User.class));
    }
}
