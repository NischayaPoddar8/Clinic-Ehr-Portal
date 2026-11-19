package com.clinic.ehr.support;

import com.clinic.ehr.model.Doctor;
import com.clinic.ehr.model.Patient;
import com.clinic.ehr.model.enums.Gender;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.test.util.ReflectionTestUtils;

/** Builders for unit tests (entities get their ids by reflection because no database assigns them). */
public final class TestData {

    /** Monday 2030-01-14, 10:00. 2030-01-21 is the following Monday. */
    public static final LocalDateTime NOW = LocalDateTime.of(2030, 1, 14, 10, 0);
    public static final LocalDate NEXT_MONDAY = LocalDate.of(2030, 1, 21);

    private TestData() { }

    public static Clock clockAt(LocalDateTime now) {
        ZoneId zone = ZoneId.systemDefault();
        return Clock.fixed(now.atZone(zone).toInstant(), zone);
    }

    public static Doctor doctor(long id) {
        Doctor doctor = new Doctor("doc" + id + "@x.com", "h", "Dan Doctor", "123", "Cardiology", "LIC-" + id);
        ReflectionTestUtils.setField(doctor, "id", id);
        return doctor;
    }

    public static Patient patient(long id) {
        Patient patient = new Patient("pat" + id + "@x.com", "h", "Pat Patient", "123",
                LocalDate.of(1990, 1, 1), Gender.FEMALE);
        ReflectionTestUtils.setField(patient, "id", id);
        return patient;
    }

    public static <T> T withId(T entity, long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
