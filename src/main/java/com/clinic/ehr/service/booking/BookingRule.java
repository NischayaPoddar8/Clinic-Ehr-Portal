package com.clinic.ehr.service.booking;

import com.clinic.ehr.exception.ClinicException;

/** One validation step of the booking pipeline. AppointmentService runs every rule, in @Order sequence. */
@FunctionalInterface
public interface BookingRule {

    /** @throws ClinicException (a subclass chosen by the rule) if the booking must be refused */
    void validate(BookingContext context);
}
