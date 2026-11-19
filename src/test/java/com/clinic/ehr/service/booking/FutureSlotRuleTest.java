package com.clinic.ehr.service.booking;

import static com.clinic.ehr.support.TestData.NOW;
import static com.clinic.ehr.support.TestData.clockAt;
import static com.clinic.ehr.support.TestData.doctor;
import static com.clinic.ehr.support.TestData.patient;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.clinic.ehr.exception.InvalidBookingException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class FutureSlotRuleTest {

    private final FutureSlotRule rule = new FutureSlotRule(clockAt(NOW));

    private BookingContext at(LocalDateTime start) {
        return new BookingContext(doctor(1L), patient(2L), start, start.plusMinutes(30), null);
    }

    @Test
    void acceptsAStartAfterNow() {
        assertThatCode(() -> rule.validate(at(NOW.plusMinutes(1)))).doesNotThrowAnyException();
    }

    @Test
    void rejectsStartsNowOrEarlier() {
        assertThatThrownBy(() -> rule.validate(at(NOW))).isInstanceOf(InvalidBookingException.class);
        assertThatThrownBy(() -> rule.validate(at(NOW.minusDays(1)))).isInstanceOf(InvalidBookingException.class);
    }
}
