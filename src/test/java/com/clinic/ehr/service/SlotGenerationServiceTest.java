package com.clinic.ehr.service;

import static com.clinic.ehr.support.TestData.NEXT_MONDAY;
import static com.clinic.ehr.support.TestData.NOW;
import static com.clinic.ehr.support.TestData.clockAt;
import static com.clinic.ehr.support.TestData.doctor;
import static com.clinic.ehr.support.TestData.patient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.clinic.ehr.dto.SlotResponse;
import com.clinic.ehr.exception.ResourceNotFoundException;
import com.clinic.ehr.model.Appointment;
import com.clinic.ehr.model.Doctor;
import com.clinic.ehr.model.DoctorAvailability;
import com.clinic.ehr.repository.AppointmentRepository;
import com.clinic.ehr.repository.DoctorAvailabilityRepository;
import com.clinic.ehr.repository.DoctorRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SlotGenerationServiceTest {

    private DoctorRepository doctorRepository;
    private DoctorAvailabilityRepository availabilityRepository;
    private AppointmentRepository appointmentRepository;
    private SlotGenerationService service;
    private Doctor doctor;

    @BeforeEach
    void setUp() {
        doctorRepository = mock(DoctorRepository.class);
        availabilityRepository = mock(DoctorAvailabilityRepository.class);
        appointmentRepository = mock(AppointmentRepository.class);
        service = new SlotGenerationService(doctorRepository, availabilityRepository, appointmentRepository, clockAt(NOW));
        doctor = doctor(5L);
        when(doctorRepository.findById(5L)).thenReturn(Optional.of(doctor));
        window(DayOfWeek.MONDAY, "09:00", "12:00");
        when(appointmentRepository.findSlotHoldingByDoctorBetween(eq(5L), any(), any())).thenReturn(List.of());
    }

    private void window(DayOfWeek day, String from, String to) {
        when(availabilityRepository.findByDoctorIdAndDayOfWeek(5L, day))
                .thenReturn(List.of(new DoctorAvailability(doctor, day, LocalTime.parse(from), LocalTime.parse(to))));
    }

    private static List<LocalTime> times(List<SlotResponse> slots) {
        return slots.stream().map(s -> s.startTime().toLocalTime()).toList();
    }

    @Test
    void generatesOneSlotPerSlotLengthAcrossTheWindow() {
        List<SlotResponse> slots = service.availableSlots(5L, NEXT_MONDAY);
        assertThat(times(slots)).containsExactly(LocalTime.of(9, 0), LocalTime.of(9, 30), LocalTime.of(10, 0),
                LocalTime.of(10, 30), LocalTime.of(11, 0), LocalTime.of(11, 30));
        assertThat(slots.get(0).endTime()).isEqualTo(NEXT_MONDAY.atTime(9, 30));
    }

    @Test
    void usesTheDoctorsSlotLength() {
        doctor.setSlotMinutes(20);
        window(DayOfWeek.MONDAY, "09:00", "10:00");
        assertThat(times(service.availableSlots(5L, NEXT_MONDAY)))
                .containsExactly(LocalTime.of(9, 0), LocalTime.of(9, 20), LocalTime.of(9, 40));
    }

    @Test
    void excludesSlotsHeldByBookedAndCompletedAppointments() {
        Appointment booked = new Appointment(patient(1L), doctor, NEXT_MONDAY.atTime(10, 0), null);
        Appointment completed = new Appointment(patient(2L), doctor, NEXT_MONDAY.atTime(9, 30), null);
        completed.complete();
        when(appointmentRepository.findSlotHoldingByDoctorBetween(eq(5L), any(), any()))
                .thenReturn(List.of(completed, booked));

        assertThat(times(service.availableSlots(5L, NEXT_MONDAY)))
                .containsExactly(LocalTime.of(9, 0), LocalTime.of(10, 30), LocalTime.of(11, 0), LocalTime.of(11, 30));
    }

    @Test
    void todayOnlyOffersSlotsAfterTheCurrentTime() {
        LocalDate today = NOW.toLocalDate(); // Monday, 10:00 now
        assertThat(times(service.availableSlots(5L, today)))
                .containsExactly(LocalTime.of(10, 30), LocalTime.of(11, 0), LocalTime.of(11, 30));
    }

    @Test
    void pastDateIsRejected() {
        assertThatThrownBy(() -> service.availableSlots(5L, NOW.toLocalDate().minusDays(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unknownOrInactiveDoctorIsNotFound() {
        assertThatThrownBy(() -> service.availableSlots(99L, NEXT_MONDAY)).isInstanceOf(ResourceNotFoundException.class);
        doctor.setActive(false);
        assertThatThrownBy(() -> service.availableSlots(5L, NEXT_MONDAY)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void dayWithoutAvailabilityHasNoSlots() {
        when(availabilityRepository.findByDoctorIdAndDayOfWeek(5L, DayOfWeek.TUESDAY)).thenReturn(List.of());
        assertThat(service.availableSlots(5L, NEXT_MONDAY.plusDays(1))).isEmpty();
    }

    @Test
    void candidateStartsIgnoreBookingsAndClock() {
        List<LocalDateTime> starts = service.candidateStarts(doctor, NOW.toLocalDate());
        assertThat(starts).hasSize(6).first().isEqualTo(NOW.toLocalDate().atTime(9, 0));
    }
}
