package com.clinic.ehr.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

/** One medication line of a medical record. Created only through MedicalRecord.addPrescription. */
@Entity
@Table(name = "prescriptions")
public class Prescription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore // back-reference; prevents JSON cycles if an entity is ever serialized by mistake
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "medical_record_id", nullable = false, updatable = false)
    private MedicalRecord medicalRecord;

    @Column(nullable = false, length = 120)
    private String medication;

    @Column(nullable = false, length = 60)
    private String dosage;

    @Column(nullable = false, length = 60)
    private String frequency;

    @Column(name = "duration_days", nullable = false)
    private int durationDays;

    @Column(length = 255)
    private String instructions;

    protected Prescription() { }

    /** Package-private on purpose: only MedicalRecord creates prescriptions. */
    Prescription(MedicalRecord medicalRecord, String medication, String dosage, String frequency,
                 int durationDays, String instructions) {
        this.medicalRecord = medicalRecord;
        this.medication = requireText(medication, "medication");
        this.dosage = requireText(dosage, "dosage");
        this.frequency = requireText(frequency, "frequency");
        if (durationDays < 1) {
            throw new IllegalArgumentException("durationDays must be at least 1");
        }
        this.durationDays = durationDays;
        this.instructions = instructions;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    public Long getId() { return id; }
    public MedicalRecord getMedicalRecord() { return medicalRecord; }
    public String getMedication() { return medication; }
    public String getDosage() { return dosage; }
    public String getFrequency() { return frequency; }
    public int getDurationDays() { return durationDays; }
    public String getInstructions() { return instructions; }
}
