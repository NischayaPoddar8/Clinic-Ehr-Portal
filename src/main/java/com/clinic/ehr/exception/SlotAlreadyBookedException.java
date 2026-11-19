package com.clinic.ehr.exception;

import org.springframework.http.HttpStatus;

/** The chosen appointment slot is already taken. */
public class SlotAlreadyBookedException extends ClinicException {

    public SlotAlreadyBookedException(String message) {
        super(message);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.CONFLICT;
    }
}
