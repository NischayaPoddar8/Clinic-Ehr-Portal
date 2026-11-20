package com.clinic.ehr.service.booking;

import com.clinic.ehr.exception.InvalidBookingException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(1)
public class FutureSlotRule implements BookingRule {

    public static final int MAX_ADVANCE_DAYS = 14;

    private final Clock clock;

    public FutureSlotRule(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void validate(BookingContext context) {
        LocalDateTime now = LocalDateTime.now(clock);
        if (!context.start().isAfter(now)) {
            throw new InvalidBookingException("The appointment must start in the future");
        }
        LocalDate maxDate = LocalDate.now(clock).plusDays(MAX_ADVANCE_DAYS);
        if (context.start().toLocalDate().isAfter(maxDate)) {
            throw new InvalidBookingException("Appointments can only be booked up to " + MAX_ADVANCE_DAYS + " days in advance");
        }
    }
}