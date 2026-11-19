package com.clinic.ehr.exception;

import org.springframework.http.HttpStatus;

/** The requested booking is not valid in itself (e.g. in the past, or not one of the doctor's slots). */
public class InvalidBookingException extends ClinicException {

    public InvalidBookingException(String message) {
        super(message);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.BAD_REQUEST;
    }
}
