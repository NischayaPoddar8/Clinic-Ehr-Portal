package com.clinic.ehr.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.clinic.ehr.model.enums.Gender;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class MedicalRecordTest {

    private MedicalRecord newRecord() {
        Patient p = new Patient("p@x.com", "h", "Pat", "1", LocalDate.of(1990, 1, 1), Gender.MALE);
        Doctor d = new Doctor("d@x.com", "h", "Doc", "2", "GP", "LIC-1");
        ReflectionTestUtils.setField(d, "id", 1L);
        Appointment a = new Appointment(p, d, LocalDateTime.of(2030, 1, 15, 10, 0), null);
        return new MedicalRecord(a, "Flu", "Rest");
    }

    @Test
    void diagnosisIsRequired() {
        MedicalRecord r = newRecord();
        assertThatThrownBy(() -> r.update(" ", "x")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void addPrescriptionLinksBackReferenceAndOverloadsWork() {
        MedicalRecord r = newRecord();
        Prescription p1 = r.addPrescription("Paracetamol", "500mg", "3x daily", 5);
        Prescription p2 = r.addPrescription("Cetirizine", "10mg", "1x daily", 7, "After food");
        assertThat(r.getPrescriptions()).containsExactly(p1, p2);
        assertThat(p1.getMedicalRecord()).isSameAs(r);
        assertThat(p1.getInstructions()).isNull();
        assertThat(p2.getInstructions()).isEqualTo("After food");
    }

    @Test
    void prescriptionListIsReadOnlyAndValidated() {
        MedicalRecord r = newRecord();
        assertThatThrownBy(() -> r.getPrescriptions().add(null)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> r.addPrescription("Drug", "1mg", "daily", 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> r.addPrescription(" ", "1mg", "daily", 1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void clearPrescriptionsRemovesAll() {
        MedicalRecord r = newRecord();
        r.addPrescription("Paracetamol", "500mg", "3x daily", 5);
        assertThat(r.getPrescriptions()).hasSize(1);
        r.clearPrescriptions();
        assertThat(r.getPrescriptions()).isEmpty();
    }
}
