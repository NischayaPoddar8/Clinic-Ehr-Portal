package com.clinic.ehr.repository;

import com.clinic.ehr.model.Doctor;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface DoctorRepository extends JpaRepository<Doctor, Long> {

    boolean existsByLicenseNumber(String licenseNumber);

    @Query("select count(d) from Doctor d where d.active = :active")
    long countByActive(@Param("active") boolean active);

    /** Active doctors whose specialization AND name contain the given texts (pass "" to ignore a filter). */
    @Query("""
           select d from Doctor d
           where d.active = true
             and lower(d.specialization) like lower(concat('%', :specialization, '%'))
             and lower(d.fullName) like lower(concat('%', :name, '%'))
           order by d.fullName
           """)
    List<Doctor> searchActive(@Param("specialization") String specialization, @Param("name") String name);

    /** SELECT ... FOR UPDATE. Booking locks the doctor row so concurrent bookings are serialized. Needs a transaction. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Doctor d where d.id = :id")
    Optional<Doctor> findByIdForUpdate(@Param("id") Long id);
}
