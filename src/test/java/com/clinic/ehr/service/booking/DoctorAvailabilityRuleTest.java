package com.clinic.ehr.service.booking;

import static com.clinic.ehr.support.TestData.NEXT_MONDAY;
import static com.clinic.ehr.support.TestData.doctor;
import static com.clinic.ehr.support.TestData.patient;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.clinic.ehr.exception.InvalidBookingException;
import com.clinic.ehr.model.Doctor;
import com.clinic.ehr.service.SlotGenerationService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DoctorAvailabilityRuleTest {

    private final Doctor doctor = doctor(1L); // 30-minute slots
    private DoctorAvailabilityRule rule;

    @BeforeEach
    void setUp() {
        SlotGenerationService slots = mock(SlotGenerationService.class);
        when(slots.candidateStarts(doctor, NEXT_MONDAY))
                .thenReturn(List.of(NEXT_MONDAY.atTime(9, 0), NEXT_MONDAY.atTime(9, 30)));
        rule = new DoctorAvailabilityRule(slots);
    }

    private BookingContext request(LocalDateTime start, LocalDateTime end) {
        return new BookingContext(doctor, patient(2L), start, end, null);
    }

    @Test
    void acceptsExactlyAGeneratedSlot() {
        LocalDateTime start = NEXT_MONDAY.atTime(9, 30);
        assertThatCode(() -> rule.validate(request(start, start.plusMinutes(30)))).doesNotThrowAnyException();
    }

    @Test
    void rejectsStartsThatAreNotGeneratedSlots() {
        LocalDateTime offGrid = NEXT_MONDAY.atTime(9, 10);
        LocalDateTime outside = NEXT_MONDAY.atTime(14, 0);
        assertThatThrownBy(() -> rule.validate(request(offGrid, offGrid.plusMinutes(30))))
                .isInstanceOf(InvalidBookingException.class);
        assertThatThrownBy(() -> rule.validate(request(outside, outside.plusMinutes(30))))
                .isInstanceOf(InvalidBookingException.class);
    }

    @Test
    void rejectsWrongLength() {
        LocalDateTime start = NEXT_MONDAY.atTime(9, 0);
        assertThatThrownBy(() -> rule.validate(request(start, start.plusMinutes(45))))
                .isInstanceOf(InvalidBookingException.class);
    }
}
