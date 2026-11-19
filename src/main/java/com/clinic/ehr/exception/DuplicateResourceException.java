package com.clinic.ehr.exception;

import org.springframework.http.HttpStatus;

/** A unique value (e.g. email, license number) is already in use. */
public class DuplicateResourceException extends ClinicException {

    public DuplicateResourceException(String message) {
        super(message);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.CONFLICT;
    }
}
