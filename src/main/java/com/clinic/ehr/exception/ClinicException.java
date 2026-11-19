package com.clinic.ehr.exception;

import org.springframework.http.HttpStatus;

/**
 * Base class of all business exceptions. Each subclass declares the HTTP status it maps to, so
 * GlobalExceptionHandler can handle every subclass through this one type (polymorphism).
 */
public abstract class ClinicException extends RuntimeException {

    protected ClinicException(String message) {
        super(message);
    }

    public abstract HttpStatus getStatus();
}
