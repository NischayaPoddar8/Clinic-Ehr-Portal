package com.clinic.ehr.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Consultation record, at most one per appointment. Patient and doctor are reached through the appointment,
 * so they are not duplicated here. Owns its prescriptions (cascade + orphanRemoval).
 */
@Entity
@Table(name = "medical_records",
       uniqueConstraints = @UniqueConstraint(name = "uk_medical_records_appointment", columnNames = "appointment_id"),
       indexes = @Index(name = "idx_medical_records_created", columnList = "created_at"))
public class MedicalRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "appointment_id", nullable = false, updatable = false)
    private Appointment appointment;

    @Column(nullable = false, length = 500)
    private String diagnosis;

    @Column(length = 4000)
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @OneToMany(mappedBy = "medicalRecord", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Prescription> prescriptions = new ArrayList<>();

    protected MedicalRecord() { }

    public MedicalRecord(Appointment appointment, String diagnosis, String notes) {
        this.appointment = Objects.requireNonNull(appointment, "appointment");
        update(diagnosis, notes);
    }

    public void update(String diagnosis, String notes) {
        if (diagnosis == null || diagnosis.isBlank()) {
            throw new IllegalArgumentException("Diagnosis is required");
        }
        this.diagnosis = diagnosis.trim();
        this.notes = notes;
    }

    /** Convenience overload without special instructions. */
    public Prescription addPrescription(String medication, String dosage, String frequency, int durationDays) {
        return addPrescription(medication, dosage, frequency, durationDays, null);
    }

    public Prescription addPrescription(String medication, String dosage, String frequency,
                                        int durationDays, String instructions) {
        Prescription p = new Prescription(this, medication, dosage, frequency, durationDays, instructions);
        prescriptions.add(p);
        return p;
    }

    public void removePrescription(Prescription prescription) {
        prescriptions.remove(prescription);
    }

    public void clearPrescriptions() {
        prescriptions.clear();
    }

    @PreUpdate
    private void touch() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Appointment getAppointment() { return appointment; }
    public String getDiagnosis() { return diagnosis; }
    public String getNotes() { return notes; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    /** Read-only view; modify through addPrescription / removePrescription. */
    public List<Prescription> getPrescriptions() { return Collections.unmodifiableList(prescriptions); }
}
