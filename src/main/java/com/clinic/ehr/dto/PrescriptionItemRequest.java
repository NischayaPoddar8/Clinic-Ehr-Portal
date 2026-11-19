package com.clinic.ehr.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record PrescriptionItemRequest(
        @NotBlank(message = "Medication is required")
        @Size(max = 120, message = "Medication must not exceed 120 characters")
        String medication,

        @NotBlank(message = "Dosage is required")
        @Size(max = 60, message = "Dosage must not exceed 60 characters")
        String dosage,

        @NotBlank(message = "Frequency is required")
        @Size(max = 60, message = "Frequency must not exceed 60 characters")
        String frequency,

        @NotNull(message = "Duration in days is required")
        @Positive(message = "Duration in days must be at least 1")
        Integer durationDays,

        @Size(max = 255, message = "Instructions must not exceed 255 characters")
        String instructions) {
}
