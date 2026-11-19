package com.clinic.ehr.exception;

import org.springframework.http.HttpStatus;

/** A scheduling clash: overlapping appointments or availability windows, or an action not allowed in the current state. */
public class AppointmentConflictException extends ClinicException {

    public AppointmentConflictException(String message) {
        super(message);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.CONFLICT;
    }
}
