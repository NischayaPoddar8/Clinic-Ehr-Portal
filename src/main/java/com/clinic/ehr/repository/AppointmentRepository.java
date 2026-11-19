package com.clinic.ehr.repository;

import com.clinic.ehr.model.Appointment;
import com.clinic.ehr.model.enums.AppointmentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    // Entity graphs load patient/doctor eagerly for these list queries (open-in-view is off).
    @EntityGraph(attributePaths = {"patient", "doctor"})
    List<Appointment> findByPatientIdOrderByStartTimeDesc(Long patientId);

    @EntityGraph(attributePaths = {"patient", "doctor"})
    List<Appointment> findByDoctorIdOrderByStartTimeDesc(Long doctorId);

    /** Non-cancelled (BOOKED or COMPLETED) appointments of a doctor starting in [from, to). */
    @Query("""
           select a from Appointment a
           where a.doctor.id = :doctorId
             and a.status <> com.clinic.ehr.model.enums.AppointmentStatus.CANCELLED
             and a.startTime >= :from and a.startTime < :to
           order by a.startTime
           """)
    List<Appointment> findSlotHoldingByDoctorBetween(@Param("doctorId") Long doctorId,
                                                     @Param("from") LocalDateTime from,
                                                     @Param("to") LocalDateTime to);

    /** Locks one appointment row (SELECT ... FOR UPDATE) so cancel/reschedule on it are serialized. Needs a transaction. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Appointment a where a.id = :id")
    Optional<Appointment> findByIdForUpdate(@Param("id") Long id);

    /**
     * True if a non-cancelled (BOOKED or COMPLETED) appointment of this doctor intersects [start, end),
     * ignoring the appointment with id excludeId (pass null when booking; the current id when rescheduling).
     */
    @Query("""
           select case when count(a) > 0 then true else false end
           from Appointment a
           where a.doctor.id = :doctorId
             and a.status <> com.clinic.ehr.model.enums.AppointmentStatus.CANCELLED
             and a.startTime < :end and a.endTime > :start
             and (:excludeId is null or a.id <> :excludeId)
           """)
    boolean existsSlotConflictForDoctor(@Param("doctorId") Long doctorId,
                                        @Param("start") LocalDateTime start,
                                        @Param("end") LocalDateTime end,
                                        @Param("excludeId") Long excludeId);

    /** Same rule for a patient, who cannot attend two appointments at once. */
    @Query("""
           select case when count(a) > 0 then true else false end
           from Appointment a
           where a.patient.id = :patientId
             and a.status <> com.clinic.ehr.model.enums.AppointmentStatus.CANCELLED
             and a.startTime < :end and a.endTime > :start
             and (:excludeId is null or a.id <> :excludeId)
           """)
    boolean existsSlotConflictForPatient(@Param("patientId") Long patientId,
                                         @Param("start") LocalDateTime start,
                                         @Param("end") LocalDateTime end,
                                         @Param("excludeId") Long excludeId);

    @Query("select count(a) from Appointment a where a.status = :status")
    long countByStatus(@Param("status") AppointmentStatus status);

    @Query("select count(a) from Appointment a where a.startTime >= :start and a.startTime < :end")
    long countBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @EntityGraph(attributePaths = {"patient", "doctor"})
    @Query("""
           select a from Appointment a
           where (:doctorId is null or a.doctor.id = :doctorId)
             and (:patientId is null or a.patient.id = :patientId)
             and (:status is null or a.status = :status)
             and (:from is null or a.startTime >= :from)
             and (:to is null or a.startTime < :to)
           order by a.startTime desc
           """)
    Page<Appointment> findFilteredForAdmin(
            @Param("doctorId") Long doctorId,
            @Param("patientId") Long patientId,
            @Param("status") AppointmentStatus status,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable);

    @EntityGraph(attributePaths = {"patient", "doctor"})
    @Query("""
           select a from Appointment a
           where a.startTime >= :start and a.startTime < :end
           order by a.startTime asc
           """)
    List<Appointment> findBetweenOrderByStartTimeAsc(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);
}
