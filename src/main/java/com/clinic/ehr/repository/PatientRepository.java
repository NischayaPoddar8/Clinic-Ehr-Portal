package com.clinic.ehr.repository;

import com.clinic.ehr.model.Patient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PatientRepository extends JpaRepository<Patient, Long> {

    @Query("select count(p) from Patient p where p.active = :active")
    long countByActive(@Param("active") boolean active);
}
