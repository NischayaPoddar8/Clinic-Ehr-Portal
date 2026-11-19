package com.clinic.ehr.service.booking;

import com.clinic.ehr.exception.InvalidBookingException;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(1)
public class FutureSlotRule implements BookingRule {

    private final Clock clock;

    public FutureSlotRule(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void validate(BookingContext context) {
        if (!context.start().isAfter(LocalDateTime.now(clock))) {
            throw new InvalidBookingException("The appointment must start in the future");
        }
    }
}
