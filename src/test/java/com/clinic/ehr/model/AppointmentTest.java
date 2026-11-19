package com.clinic.ehr.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.clinic.ehr.model.enums.AppointmentStatus;
import com.clinic.ehr.model.enums.Gender;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AppointmentTest {

    private static final LocalDateTime START = LocalDateTime.of(2030, 1, 15, 10, 0);
    private Patient patient;
    private Doctor doctor;

    @BeforeEach
    void setUp() {
        patient = new Patient("p@x.com", "h", "Pat", "1", LocalDate.of(1990, 1, 1), Gender.FEMALE);
        doctor = new Doctor("d@x.com", "h", "Doc", "2", "Cardiology", "LIC-1");
        ReflectionTestUtils.setField(doctor, "id", 5L);
    }

    @Test
    void constructorBooksSlotUsingDoctorSlotLength() {
        Appointment a = new Appointment(patient, doctor, START, "checkup");
        assertThat(a.getStatus()).isEqualTo(AppointmentStatus.BOOKED);
        assertThat(a.getEndTime()).isEqualTo(START.plusMinutes(30));
        assertThat(a.getSlotKey()).isEqualTo("5_203001151000");
    }

    @Test
    void constructorRejectsUnsavedDoctor() {
        Doctor unsaved = new Doctor("u@x.com", "h", "U", "3", "ENT", "LIC-2");
        assertThatThrownBy(() -> new Appointment(patient, unsaved, START, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cancelReleasesSlotKey() {
        Appointment a = new Appointment(patient, doctor, START, null);
        a.cancel();
        assertThat(a.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(a.getSlotKey()).isNull();
    }

    @Test
    void cannotCancelTwiceOrCompleteAfterCancel() {
        Appointment a = new Appointment(patient, doctor, START, null);
        a.cancel();
        assertThatThrownBy(a::cancel).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(a::complete).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void completeKeepsSlotKey() {
        Appointment a = new Appointment(patient, doctor, START, null);
        a.complete();
        assertThat(a.getStatus()).isEqualTo(AppointmentStatus.COMPLETED);
        assertThat(a.getSlotKey()).isNotNull();
    }

    @Test
    void rescheduleMovesTimesAndKeyKeepingDuration() {
        Appointment a = new Appointment(patient, doctor, START, null);
        LocalDateTime newStart = START.plusDays(1);
        a.reschedule(newStart);
        assertThat(a.getStartTime()).isEqualTo(newStart);
        assertThat(a.getDuration()).isEqualTo(Duration.ofMinutes(30));
        assertThat(a.getSlotKey()).isEqualTo("5_203001161000");
    }

    @Test
    void rescheduleRejectedWhenNotBooked() {
        Appointment a = new Appointment(patient, doctor, START, null);
        a.cancel();
        assertThatThrownBy(() -> a.reschedule(START.plusDays(1))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void overlapsIsHalfOpen() {
        Appointment a = new Appointment(patient, doctor, START, null); // 10:00-10:30
        assertThat(a.overlaps(START.plusMinutes(15), START.plusMinutes(45))).isTrue();
        assertThat(a.overlaps(START.plusMinutes(30), START.plusMinutes(60))).isFalse();
    }

    @Test
    void rescheduleWithExplicitEndUsesTheNewLength() {
        Appointment a = new Appointment(patient, doctor, START, null);
        a.reschedule(START.plusDays(1), START.plusDays(1).plusMinutes(45));
        assertThat(a.getDuration()).isEqualTo(Duration.ofMinutes(45));
        assertThat(a.getSlotKey()).isEqualTo("5_203001161000");
    }

    @Test
    void rescheduleRejectsAnEndNotAfterTheStart() {
        Appointment a = new Appointment(patient, doctor, START, null);
        assertThatThrownBy(() -> a.reschedule(START.plusDays(1), START.plusDays(1))).isInstanceOf(IllegalArgumentException.class);
    }
}
