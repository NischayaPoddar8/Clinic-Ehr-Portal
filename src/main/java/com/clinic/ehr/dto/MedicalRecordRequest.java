package com.clinic.ehr.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record MedicalRecordRequest(
        @NotBlank(message = "Diagnosis is required")
        @Size(max = 500, message = "Diagnosis must not exceed 500 characters")
        String diagnosis,

        @Size(max = 4000, message = "Notes must not exceed 4000 characters")
        String notes,

        List<@Valid PrescriptionItemRequest> prescriptions) {
}
