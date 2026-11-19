package com.clinic.ehr.dto;

import jakarta.validation.constraints.*;

public record UpdateDoctorProfileRequest(
        @NotBlank @Size(max = 100) String fullName,
        @Pattern(regexp = "^[0-9+\\-\\s]{7,20}$", message = "Phone must be 7-20 digits, spaces, + or -") String phone,
        @NotBlank @Size(max = 80) String specialization,
        @Size(max = 120) String qualification,
        @NotNull @Min(10) @Max(120) Integer slotMinutes) { }
