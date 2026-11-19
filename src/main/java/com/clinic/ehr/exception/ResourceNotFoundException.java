package com.clinic.ehr.exception;

import org.springframework.http.HttpStatus;

/** The requested record does not exist. */
public class ResourceNotFoundException extends ClinicException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.NOT_FOUND;
    }
}
