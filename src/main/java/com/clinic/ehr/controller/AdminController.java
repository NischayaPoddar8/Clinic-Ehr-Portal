package com.clinic.ehr.controller;

import com.clinic.ehr.dto.AppointmentResponse;
import com.clinic.ehr.dto.AuditLogResponse;
import com.clinic.ehr.dto.ClinicStatsResponse;
import com.clinic.ehr.dto.CreateDoctorRequest;
import com.clinic.ehr.dto.UpdateUserRequest;
import com.clinic.ehr.dto.UpdateUserStatusRequest;
import com.clinic.ehr.dto.UserResponse;
import com.clinic.ehr.model.enums.AppointmentStatus;
import com.clinic.ehr.model.enums.Role;
import com.clinic.ehr.security.CustomUserDetails;
import com.clinic.ehr.service.AdminStatsService;
import com.clinic.ehr.service.AppointmentService;
import com.clinic.ehr.service.AuditService;
import com.clinic.ehr.service.DoctorAccountService;
import com.clinic.ehr.service.ReportExporter;
import com.clinic.ehr.service.UserService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** ADMIN-only endpoints (enforced for /api/admin/** in SecurityConfig). */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final DoctorAccountService doctorAccountService;
    private final AuditService auditService;
    private final UserService userService;
    private final AppointmentService appointmentService;
    private final AdminStatsService adminStatsService;
    private final ReportExporter reportExporter;

    public AdminController(DoctorAccountService doctorAccountService,
                           AuditService auditService,
                           UserService userService,
                           AppointmentService appointmentService,
                           AdminStatsService adminStatsService,
                           ReportExporter reportExporter) {
        this.doctorAccountService = doctorAccountService;
        this.auditService = auditService;
        this.userService = userService;
        this.appointmentService = appointmentService;
        this.adminStatsService = adminStatsService;
        this.reportExporter = reportExporter;
    }

    @PostMapping("/doctors")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse createDoctor(@Valid @RequestBody CreateDoctorRequest request,
                                     @AuthenticationPrincipal CustomUserDetails admin) {
        return doctorAccountService.create(request, admin.getId());
    }

    @GetMapping("/audit-logs")
    public List<AuditLogResponse> auditLogs(@RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return auditService.recent(page, size);
    }

    @GetMapping("/users")
    public List<UserResponse> listUsers(
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return userService.listUsers(role, active, page, size);
    }

    @PutMapping("/users/{id}")
    public UserResponse updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRequest request,
            @AuthenticationPrincipal CustomUserDetails admin) {
        return userService.updateUser(id, request, admin.getId());
    }

    @PatchMapping("/users/{id}/status")
    public UserResponse updateUserStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserStatusRequest request,
            @AuthenticationPrincipal CustomUserDetails admin) {
        return userService.updateStatus(id, request.active(), admin.getId());
    }

    @GetMapping("/appointments")
    public List<AppointmentResponse> listAppointments(
            @RequestParam(required = false) Long doctorId,
            @RequestParam(required = false) Long patientId,
            @RequestParam(required = false) AppointmentStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return appointmentService.listForAdmin(doctorId, patientId, status, date, page, size);
    }

    @PostMapping("/appointments/{id}/cancel")
    public AppointmentResponse cancelAppointment(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails admin) {
        return appointmentService.adminCancel(id, admin.getId());
    }

    @GetMapping("/stats")
    public ClinicStatsResponse stats() {
        return adminStatsService.getStats();
    }

    @GetMapping("/reports/appointments")
    public ResponseEntity<byte[]> exportAppointments(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "csv") String format,
            @AuthenticationPrincipal CustomUserDetails admin) {
        ReportExporter.ExportResult result = reportExporter.exportAppointments(from, to, format, admin.getId());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + result.filename() + "\"")
                .contentType(MediaType.parseMediaType(result.mediaType()))
                .body(result.content());
    }
}
