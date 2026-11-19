package com.clinic.ehr.dto;

import jakarta.validation.constraints.*;

/** Admin-only creation of a doctor account. */
public record CreateDoctorRequest(
        @NotBlank @Size(max = 100) String fullName,
        @NotBlank @Email @Size(max = 120) String email,
        @NotBlank @Size(min = 8, max = 72, message = "Password must be 8-72 characters")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).*$", message = "Password must contain a letter and a digit")
        String password,
        @Pattern(regexp = "^[0-9+\\-\\s]{7,20}$", message = "Phone must be 7-20 digits, spaces, + or -") String phone,
        @NotBlank @Size(max = 80) String specialization,
        @NotBlank @Size(max = 40) String licenseNumber,
        @Size(max = 120) String qualification,
        @Min(10) @Max(120) Integer slotMinutes) implements AccountRequest {

    @Override
    public String toString() {
        return "CreateDoctorRequest[email=" + email + ", fullName=" + fullName + "]";
    }
}
