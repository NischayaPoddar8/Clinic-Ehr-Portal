package com.clinic.ehr.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @NotBlank(message = "Full name is required")
        @Size(max = 100)
        String fullName,

        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        @Size(max = 120)
        String email,

        @Size(max = 20)
        String phone,

        @Size(max = 80)
        String specialization,

        @Size(max = 120)
        String qualification,

        @Min(10) @Max(120)
        Integer slotMinutes
) {}