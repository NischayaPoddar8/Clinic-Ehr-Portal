package com.clinic.ehr.dto;

import com.clinic.ehr.model.enums.Gender;
import jakarta.validation.constraints.*;
import java.time.LocalDate;

/** Public patient registration. There is deliberately no role field: the server always creates a PATIENT. */
public record RegisterRequest(
        @NotBlank @Size(max = 100) String fullName,
        @NotBlank @Email @Size(max = 120) String email,
        @NotBlank @Size(min = 8, max = 72, message = "Password must be 8-72 characters")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).*$", message = "Password must contain a letter and a digit")
        String password,
        @Pattern(regexp = "^[0-9+\\-\\s]{7,20}$", message = "Phone must be 7-20 digits, spaces, + or -") String phone,
        @NotNull @Past(message = "Date of birth must be in the past") LocalDate dateOfBirth,
        @NotNull Gender gender,
        @Size(max = 255) String address,
        @Size(max = 5) String bloodGroup,
        @Size(max = 100) String emergencyContact) implements AccountRequest {

    @Override
    public String toString() { // never print the password
        return "RegisterRequest[email=" + email + ", fullName=" + fullName + "]";
    }
}
