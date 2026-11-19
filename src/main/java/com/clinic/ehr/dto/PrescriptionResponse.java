package com.clinic.ehr.dto;

import com.clinic.ehr.model.Prescription;

public record PrescriptionResponse(
        Long id,
        String medication,
        String dosage,
        String frequency,
        int durationDays,
        String instructions) {

    public static PrescriptionResponse from(Prescription p) {
        return new PrescriptionResponse(
                p.getId(),
                p.getMedication(),
                p.getDosage(),
                p.getFrequency(),
                p.getDurationDays(),
                p.getInstructions());
    }
}
