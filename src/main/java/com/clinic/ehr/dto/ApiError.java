package com.clinic.ehr.dto;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;

/** Uniform JSON error body returned by every failing API call. */
public record ApiError(LocalDateTime timestamp, int status, String error, String message,
                       List<FieldIssue> fieldErrors) {

    public record FieldIssue(String field, String message) { }

    public static ApiError of(HttpStatus status, String message) {
        return of(status, message, List.of());
    }

    public static ApiError of(HttpStatus status, String message, List<FieldIssue> fieldErrors) {
        return new ApiError(LocalDateTime.now(), status.value(), status.getReasonPhrase(), message, fieldErrors);
    }
}
