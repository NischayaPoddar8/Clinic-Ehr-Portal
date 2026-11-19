package com.clinic.ehr.service;

import static com.clinic.ehr.support.TestData.doctor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.clinic.ehr.dto.DoctorResponse;
import com.clinic.ehr.dto.UpdateDoctorProfileRequest;
import com.clinic.ehr.exception.ResourceNotFoundException;
import com.clinic.ehr.model.Doctor;
import com.clinic.ehr.model.enums.AuditAction;
import com.clinic.ehr.model.enums.AuditEntityType;
import com.clinic.ehr.repository.DoctorRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DoctorServiceTest {

    private DoctorRepository doctorRepository;
    private AuditService auditService;
    private DoctorService service;
    private Doctor doctor;

    @BeforeEach
    void setUp() {
        doctorRepository = mock(DoctorRepository.class);
        auditService = mock(AuditService.class);
        service = new DoctorService(doctorRepository, auditService);
        doctor = doctor(5L);
        when(doctorRepository.findById(5L)).thenReturn(Optional.of(doctor));
    }

    @Test
    void searchTrimsFiltersAndTreatsMissingOnesAsEmpty() {
        when(doctorRepository.searchActive("", "")).thenReturn(List.of(doctor));
        when(doctorRepository.searchActive("cardio", "dan")).thenReturn(List.of(doctor));

        assertThat(service.search(null, null)).hasSize(1);
        List<DoctorResponse> result = service.search("  cardio ", " dan");
        assertThat(result).hasSize(1);
        assertThat(result.get(0).displayName()).isEqualTo("Dr. Dan Doctor");
    }

    @Test
    void getActiveDoctorHidesInactiveAndMissingDoctors() {
        assertThat(service.getActiveDoctor(5L).id()).isEqualTo(5L);
        doctor.setActive(false);
        assertThatThrownBy(() -> service.getActiveDoctor(5L)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.getActiveDoctor(99L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateProfileChangesFieldsAndAudits() {
        DoctorResponse response = service.updateProfile(5L,
                new UpdateDoctorProfileRequest(" Dana Doc ", "555 1234", " Dermatology ", "MD, PhD", 20));

        assertThat(doctor.getFullName()).isEqualTo("Dana Doc");
        assertThat(doctor.getPhone()).isEqualTo("555 1234");
        assertThat(doctor.getSpecialization()).isEqualTo("Dermatology");
        assertThat(doctor.getSlotMinutes()).isEqualTo(20);
        assertThat(response.displayName()).isEqualTo("Dr. Dana Doc");
        verify(auditService).record(same(doctor), eq(AuditAction.UPDATE), eq(AuditEntityType.USER), eq(5L), anyString());
    }

    @Test
    void updateProfileOfUnknownDoctorIsNotFound() {
        assertThatThrownBy(() -> service.updateProfile(99L,
                new UpdateDoctorProfileRequest("A", null, "B", null, 30))).isInstanceOf(ResourceNotFoundException.class);
    }
}
