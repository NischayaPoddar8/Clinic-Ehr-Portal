package com.clinic.ehr.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class DoctorAvailabilityTest {

    private final Doctor doctor = new Doctor("d@x.com", "h", "Doc", "2", "Cardiology", "LIC-1");

    private DoctorAvailability window(String from, String to) {
        return new DoctorAvailability(doctor, DayOfWeek.MONDAY, LocalTime.parse(from), LocalTime.parse(to));
    }

    @Test
    void rejectsEndNotAfterStart() {
        assertThatThrownBy(() -> window("10:00", "10:00")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> window("11:00", "10:00")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void generatesWholeSlotsOnly() {
        assertThat(window("09:00", "11:00").generateSlotStarts(30))
                .containsExactly(LocalTime.of(9, 0), LocalTime.of(9, 30), LocalTime.of(10, 0), LocalTime.of(10, 30));
        assertThat(window("09:00", "10:20").generateSlotStarts(30)).hasSize(2);
    }

    @Test
    void windowEndingNearMidnightTerminates() {
        assertThat(window("23:00", "23:59").generateSlotStarts(30)).hasSize(1);
    }

    @Test
    void coversChecksSlotInsideWindow() {
        DoctorAvailability w = window("09:00", "12:00");
        assertThat(w.covers(LocalTime.of(9, 0), LocalTime.of(9, 30))).isTrue();
        assertThat(w.covers(LocalTime.of(11, 45), LocalTime.of(12, 15))).isFalse();
    }

    @Test
    void overlapsIgnoresWindowsThatOnlyTouch() {
        DoctorAvailability w = window("09:00", "12:00");
        assertThat(w.overlaps(LocalTime.of(11, 0), LocalTime.of(13, 0))).isTrue();
        assertThat(w.overlaps(LocalTime.of(8, 0), LocalTime.of(9, 30))).isTrue();
        assertThat(w.overlaps(LocalTime.of(12, 0), LocalTime.of(14, 0))).isFalse();
        assertThat(w.overlaps(LocalTime.of(7, 0), LocalTime.of(9, 0))).isFalse();
    }
}
