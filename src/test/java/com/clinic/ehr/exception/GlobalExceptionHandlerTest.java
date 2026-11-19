package com.clinic.ehr.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.clinic.ehr.dto.ApiError;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void everyClinicExceptionMapsToItsOwnStatusThroughOneHandler() {
        Map<ClinicException, Integer> expected = new LinkedHashMap<>();
        expected.put(new ResourceNotFoundException("missing"), 404);
        expected.put(new SlotAlreadyBookedException("taken"), 409);
        expected.put(new AppointmentConflictException("clash"), 409);
        expected.put(new UnauthorizedAccessException("not yours"), 403);
        expected.put(new DuplicateResourceException("dup"), 409);

        expected.forEach((exception, status) -> {
            ResponseEntity<ApiError> response = handler.handleClinicException(exception);
            assertThat(response.getStatusCode().value()).isEqualTo(status);
            assertThat(response.getBody().message()).isEqualTo(exception.getMessage());
            assertThat(response.getBody().status()).isEqualTo(status);
        });
    }

    @Test
    void authenticationFailureNeverRevealsWhichPartWasWrong() {
        ResponseEntity<ApiError> response = handler.handleAuthentication(new BadCredentialsException("User not found"));
        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getBody().message()).isEqualTo("Invalid email or password");
    }

    @Test
    void accessDeniedIs403() {
        assertThat(handler.handleAccessDenied(new AccessDeniedException("x")).getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void domainInvariantViolationsMapToClientErrors() {
        assertThat(handler.handleIllegalArgument(new IllegalArgumentException("bad")).getStatusCode().value()).isEqualTo(400);
        assertThat(handler.handleIllegalState(new IllegalStateException("state")).getStatusCode().value()).isEqualTo(409);
        assertThat(handler.handleDataIntegrity(new DataIntegrityViolationException("dup key")).getStatusCode().value())
                .isEqualTo(409);
    }

    @Test
    void unexpectedErrorsDoNotLeakInternals() {
        ResponseEntity<ApiError> response = handler.handleUnexpected(new RuntimeException("SELECT secret FROM db"));
        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody().message()).doesNotContain("SELECT");
    }
}
