package com.clinic.ehr.exception;

import org.springframework.http.HttpStatus;

/** The signed-in user may not access or change this resource (authenticated, but not permitted). */
public class UnauthorizedAccessException extends ClinicException {

    public UnauthorizedAccessException(String message) {
        super(message);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.FORBIDDEN;
    }
}
