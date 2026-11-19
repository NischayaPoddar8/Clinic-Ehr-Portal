package com.clinic.ehr.model;

import com.clinic.ehr.model.enums.Role;
import jakarta.persistence.*;

@Entity
@Table(name = "doctors",
       uniqueConstraints = @UniqueConstraint(name = "uk_doctors_license", columnNames = "license_number"),
       indexes = @Index(name = "idx_doctors_specialization", columnList = "specialization"))
@PrimaryKeyJoinColumn(name = "user_id")
public class Doctor extends User {

    @Column(nullable = false, length = 80)
    private String specialization;

    @Column(name = "license_number", nullable = false, length = 40)
    private String licenseNumber;

    @Column(length = 120)
    private String qualification;

    /** Length of one appointment slot. */
    @Column(name = "slot_minutes", nullable = false)
    private int slotMinutes = 30;

    protected Doctor() { }

    public Doctor(String email, String passwordHash, String fullName, String phone,
                  String specialization, String licenseNumber) {
        super(email, passwordHash, fullName, phone, Role.DOCTOR);
        this.specialization = specialization;
        this.licenseNumber = licenseNumber;
    }

    @Override
    public String getDisplayName() {
        return "Dr. " + getFullName();
    }

    public String getSpecialization() { return specialization; }
    public void setSpecialization(String specialization) { this.specialization = specialization; }
    public String getLicenseNumber() { return licenseNumber; }
    public String getQualification() { return qualification; }
    public void setQualification(String qualification) { this.qualification = qualification; }
    public int getSlotMinutes() { return slotMinutes; }
    public void setSlotMinutes(int slotMinutes) {
        if (slotMinutes < 10 || slotMinutes > 120) {
            throw new IllegalArgumentException("slotMinutes must be between 10 and 120");
        }
        this.slotMinutes = slotMinutes;
    }
}
