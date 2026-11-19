package com.clinic.ehr.service;

import static com.clinic.ehr.support.TestData.doctor;
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

import com.clinic.ehr.dto.AvailabilityRequest;
import com.clinic.ehr.dto.AvailabilityResponse;
import com.clinic.ehr.exception.AppointmentConflictException;
import com.clinic.ehr.exception.ResourceNotFoundException;
import com.clinic.ehr.exception.UnauthorizedAccessException;
import com.clinic.ehr.model.Doctor;
import com.clinic.ehr.model.DoctorAvailability;
import com.clinic.ehr.model.enums.AuditAction;
import com.clinic.ehr.model.enums.AuditEntityType;
import com.clinic.ehr.repository.DoctorAvailabilityRepository;
import com.clinic.ehr.repository.DoctorRepository;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DoctorAvailabilityServiceTest {

    private DoctorRepository doctorRepository;
    private DoctorAvailabilityRepository availabilityRepository;
    private AuditService auditService;
    private DoctorAvailabilityService service;
    private Doctor doctor;

    @BeforeEach
    void setUp() {
        doctorRepository = mock(DoctorRepository.class);
        availabilityRepository = mock(DoctorAvailabilityRepository.class);
        auditService = mock(AuditService.class);
        service = new DoctorAvailabilityService(doctorRepository, availabilityRepository, auditService);
        doctor = doctor(5L); // 30-minute slots
        when(doctorRepository.findById(5L)).thenReturn(Optional.of(doctor));
        when(availabilityRepository.findByDoctorIdAndDayOfWeek(5L, DayOfWeek.MONDAY)).thenReturn(List.of());
        when(availabilityRepository.save(any(DoctorAvailability.class))).thenAnswer(inv -> withId(inv.getArgument(0), 40L));
    }

    private static AvailabilityRequest request(String from, String to) {
        return new AvailabilityRequest(DayOfWeek.MONDAY, LocalTime.parse(from), LocalTime.parse(to));
    }

    private DoctorAvailability existing(long id, String from, String to) {
        return withId(new DoctorAvailability(doctor, DayOfWeek.MONDAY, LocalTime.parse(from), LocalTime.parse(to)), id);
    }

    @Test
    void addsWindowAndAudits() {
        AvailabilityResponse response = service.add(5L, request("09:00", "12:00"));
        assertThat(response.id()).isEqualTo(40L);
        assertThat(response.startTime()).isEqualTo(LocalTime.of(9, 0));
        verify(auditService).record(same(doctor), eq(AuditAction.CREATE), eq(AuditEntityType.AVAILABILITY), eq(40L), anyString());
    }

    @Test
    void rejectsEndNotAfterStartAndWindowsShorterThanOneSlot() {
        assertThatThrownBy(() -> service.add(5L, request("12:00", "09:00"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.add(5L, request("09:00", "09:20"))).isInstanceOf(IllegalArgumentException.class);
        verify(availabilityRepository, never()).save(any(DoctorAvailability.class));
    }

    @Test
    void rejectsOverlapWithAnExistingWindowOnTheSameDay() {
        when(availabilityRepository.findByDoctorIdAndDayOfWeek(5L, DayOfWeek.MONDAY))
                .thenReturn(List.of(existing(1L, "09:00", "12:00")));
        assertThatThrownBy(() -> service.add(5L, request("11:00", "13:00"))).isInstanceOf(AppointmentConflictException.class);
        assertThatThrownBy(() -> service.add(5L, request("08:00", "09:30"))).isInstanceOf(AppointmentConflictException.class);
        assertThatThrownBy(() -> service.add(5L, request("09:30", "10:30"))).isInstanceOf(AppointmentConflictException.class);
    }

    @Test
    void allowsWindowsThatOnlyTouch() {
        when(availabilityRepository.findByDoctorIdAndDayOfWeek(5L, DayOfWeek.MONDAY))
                .thenReturn(List.of(existing(1L, "09:00", "12:00")));
        assertThat(service.add(5L, request("12:00", "15:00")).id()).isEqualTo(40L);
    }

    @Test
    void unknownDoctorIsNotFound() {
        assertThatThrownBy(() -> service.add(99L, request("09:00", "12:00"))).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listsOrderedByDayThenStart() {
        when(availabilityRepository.findByDoctorId(5L)).thenReturn(List.of(
                new DoctorAvailability(doctor, DayOfWeek.WEDNESDAY, LocalTime.of(9, 0), LocalTime.of(10, 0)),
                new DoctorAvailability(doctor, DayOfWeek.MONDAY, LocalTime.of(14, 0), LocalTime.of(16, 0)),
                new DoctorAvailability(doctor, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(12, 0))));
        assertThat(service.list(5L)).extracting(r -> r.dayOfWeek() + " " + r.startTime())
                .containsExactly("MONDAY 09:00", "MONDAY 14:00", "WEDNESDAY 09:00");
    }

    @Test
    void ownerCanDeleteAndItIsAudited() {
        DoctorAvailability window = existing(40L, "09:00", "12:00");
        when(availabilityRepository.findById(40L)).thenReturn(Optional.of(window));
        service.delete(5L, 40L);
        verify(availabilityRepository).delete(window);
        verify(auditService).record(same(doctor), eq(AuditAction.DELETE), eq(AuditEntityType.AVAILABILITY), eq(40L), anyString());
    }

    @Test
    void anotherDoctorCannotDelete() {
        DoctorAvailability window = existing(40L, "09:00", "12:00");
        when(availabilityRepository.findById(40L)).thenReturn(Optional.of(window));
        assertThatThrownBy(() -> service.delete(6L, 40L)).isInstanceOf(UnauthorizedAccessException.class);
        verify(availabilityRepository, never()).delete(any(DoctorAvailability.class));
    }

    @Test
    void deletingUnknownWindowIsNotFound() {
        when(availabilityRepository.findById(404L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.delete(5L, 404L)).isInstanceOf(ResourceNotFoundException.class);
    }
}
