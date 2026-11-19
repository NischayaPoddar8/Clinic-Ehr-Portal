package com.clinic.ehr.controller;

import com.clinic.ehr.dto.AppointmentResponse;
import com.clinic.ehr.dto.AvailabilityRequest;
import com.clinic.ehr.dto.AvailabilityResponse;
import com.clinic.ehr.dto.DoctorResponse;
import com.clinic.ehr.dto.MedicalRecordRequest;
import com.clinic.ehr.dto.MedicalRecordResponse;
import com.clinic.ehr.dto.UpdateDoctorProfileRequest;
import com.clinic.ehr.model.enums.AppointmentStatus;
import com.clinic.ehr.model.enums.Role;
import com.clinic.ehr.security.CustomUserDetails;
import com.clinic.ehr.service.AppointmentService;
import com.clinic.ehr.service.DoctorAvailabilityService;
import com.clinic.ehr.service.DoctorService;
import com.clinic.ehr.service.MedicalRecordService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** DOCTOR-only endpoints (enforced for /api/doctor/** in SecurityConfig). */
@RestController
@RequestMapping("/api/doctor")
public class DoctorController {

    private final DoctorService doctorService;
    private final DoctorAvailabilityService availabilityService;
    private final AppointmentService appointmentService;
    private final MedicalRecordService medicalRecordService;

    public DoctorController(DoctorService doctorService, DoctorAvailabilityService availabilityService,
                            AppointmentService appointmentService, MedicalRecordService medicalRecordService) {
        this.doctorService = doctorService;
        this.availabilityService = availabilityService;
        this.appointmentService = appointmentService;
        this.medicalRecordService = medicalRecordService;
    }

    @PutMapping("/profile")
    public DoctorResponse updateProfile(@Valid @RequestBody UpdateDoctorProfileRequest request,
                                        @AuthenticationPrincipal CustomUserDetails doctor) {
        return doctorService.updateProfile(doctor.getId(), request);
    }

    @PostMapping("/availability")
    @ResponseStatus(HttpStatus.CREATED)
    public AvailabilityResponse addAvailability(@Valid @RequestBody AvailabilityRequest request,
                                                @AuthenticationPrincipal CustomUserDetails doctor) {
        return availabilityService.add(doctor.getId(), request);
    }

    @GetMapping("/availability")
    public List<AvailabilityResponse> listAvailability(@AuthenticationPrincipal CustomUserDetails doctor) {
        return availabilityService.list(doctor.getId());
    }

    @DeleteMapping("/availability/{availabilityId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAvailability(@PathVariable Long availabilityId, @AuthenticationPrincipal CustomUserDetails doctor) {
        availabilityService.delete(doctor.getId(), availabilityId);
    }

    @GetMapping("/appointments")
    public List<AppointmentResponse> appointments(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) AppointmentStatus status,
            @AuthenticationPrincipal CustomUserDetails doctor) {
        return appointmentService.listForDoctor(doctor.getId(), date, status);
    }

    @PostMapping("/appointments/{appointmentId}/cancel")
    public AppointmentResponse cancel(@PathVariable Long appointmentId, @AuthenticationPrincipal CustomUserDetails doctor) {
        return appointmentService.cancel(appointmentId, doctor.getId(), Role.DOCTOR);
    }

    @PostMapping("/appointments/{appointmentId}/complete")
    public AppointmentResponse complete(@PathVariable Long appointmentId, @AuthenticationPrincipal CustomUserDetails doctor) {
        return appointmentService.complete(appointmentId, doctor.getId());
    }

    @PostMapping("/appointments/{appointmentId}/medical-record")
    @ResponseStatus(HttpStatus.CREATED)
    public MedicalRecordResponse createMedicalRecord(@PathVariable Long appointmentId,
                                                     @Valid @RequestBody MedicalRecordRequest request,
                                                     @AuthenticationPrincipal CustomUserDetails doctor) {
        return medicalRecordService.create(appointmentId, doctor.getId(), request);
    }

    @PutMapping("/medical-records/{id}")
    public MedicalRecordResponse updateMedicalRecord(@PathVariable Long id,
                                                     @Valid @RequestBody MedicalRecordRequest request,
                                                     @AuthenticationPrincipal CustomUserDetails doctor) {
        return medicalRecordService.update(id, doctor.getId(), request);
    }

    @GetMapping("/medical-records/{id}")
    public MedicalRecordResponse getMedicalRecord(@PathVariable Long id,
                                                  @AuthenticationPrincipal CustomUserDetails doctor) {
        return medicalRecordService.getForDoctor(id, doctor.getId());
    }

    @GetMapping("/patients/{patientId}/medical-records")
    public List<MedicalRecordResponse> getPatientMedicalRecords(@PathVariable Long patientId,
                                                               @AuthenticationPrincipal CustomUserDetails doctor) {
        return medicalRecordService.listForDoctorByPatient(doctor.getId(), patientId);
    }
}
