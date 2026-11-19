package com.clinic.ehr.controller;

import com.clinic.ehr.dto.AppointmentResponse;
import com.clinic.ehr.dto.BookAppointmentRequest;
import com.clinic.ehr.dto.MedicalRecordResponse;
import com.clinic.ehr.dto.RescheduleRequest;
import com.clinic.ehr.model.enums.AppointmentStatus;
import com.clinic.ehr.model.enums.Role;
import com.clinic.ehr.security.CustomUserDetails;
import com.clinic.ehr.service.AppointmentService;
import com.clinic.ehr.service.MedicalRecordService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** PATIENT-only endpoints (enforced for /api/patient/** in SecurityConfig). */
@RestController
@RequestMapping("/api/patient")
public class PatientController {

    private final AppointmentService appointmentService;
    private final MedicalRecordService medicalRecordService;

    public PatientController(AppointmentService appointmentService, MedicalRecordService medicalRecordService) {
        this.appointmentService = appointmentService;
        this.medicalRecordService = medicalRecordService;
    }

    @PostMapping("/appointments")
    @ResponseStatus(HttpStatus.CREATED)
    public AppointmentResponse book(@Valid @RequestBody BookAppointmentRequest request,
                                    @AuthenticationPrincipal CustomUserDetails patient) {
        return appointmentService.book(patient.getId(), request);
    }

    @GetMapping("/appointments")
    public List<AppointmentResponse> myAppointments(@RequestParam(required = false) AppointmentStatus status,
                                                    @AuthenticationPrincipal CustomUserDetails patient) {
        return appointmentService.listForPatient(patient.getId(), status);
    }

    @PostMapping("/appointments/{appointmentId}/cancel")
    public AppointmentResponse cancel(@PathVariable Long appointmentId, @AuthenticationPrincipal CustomUserDetails patient) {
        return appointmentService.cancel(appointmentId, patient.getId(), Role.PATIENT);
    }

    @PutMapping("/appointments/{appointmentId}/reschedule")
    public AppointmentResponse reschedule(@PathVariable Long appointmentId,
                                          @Valid @RequestBody RescheduleRequest request,
                                          @AuthenticationPrincipal CustomUserDetails patient) {
        return appointmentService.reschedule(appointmentId, patient.getId(), request);
    }

    @GetMapping("/medical-records")
    public List<MedicalRecordResponse> listMedicalRecords(@AuthenticationPrincipal CustomUserDetails patient) {
        return medicalRecordService.listForPatient(patient.getId());
    }

    @GetMapping("/medical-records/{id}")
    public MedicalRecordResponse getMedicalRecord(@PathVariable Long id,
                                                  @AuthenticationPrincipal CustomUserDetails patient) {
        return medicalRecordService.getForPatient(id, patient.getId());
    }
}
