package com.clinic.ehr.repository;

import com.clinic.ehr.model.DoctorAvailability;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.DayOfWeek;
import java.util.List;

public interface DoctorAvailabilityRepository extends JpaRepository<DoctorAvailability, Long> {
    // Unordered on purpose: day_of_week is stored as text, so the service sorts by DayOfWeek in Java.
    List<DoctorAvailability> findByDoctorId(Long doctorId);
    List<DoctorAvailability> findByDoctorIdAndDayOfWeek(Long doctorId, DayOfWeek dayOfWeek);
}
