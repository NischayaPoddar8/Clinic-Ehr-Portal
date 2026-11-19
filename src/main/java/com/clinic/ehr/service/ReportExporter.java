package com.clinic.ehr.service;

import com.clinic.ehr.exception.InvalidBookingException;
import com.clinic.ehr.exception.ResourceNotFoundException;
import com.clinic.ehr.model.Appointment;
import com.clinic.ehr.model.User;
import com.clinic.ehr.model.enums.AuditAction;
import com.clinic.ehr.model.enums.AuditEntityType;
import com.clinic.ehr.repository.AppointmentRepository;
import com.clinic.ehr.repository.UserRepository;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportExporter {

    private static final Logger log = LoggerFactory.getLogger(ReportExporter.class);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final AppointmentRepository appointmentRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final String exportDir;

    public record ExportResult(byte[] content, String mediaType, String filename) { }

    public ReportExporter(AppointmentRepository appointmentRepository,
                          UserRepository userRepository,
                          AuditService auditService,
                          @Value("${app.export.dir:exports}") String exportDir) {
        this.appointmentRepository = appointmentRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.exportDir = exportDir;
    }

    @Transactional
    public ExportResult exportAppointments(LocalDate from, LocalDate to, String format, Long adminId) {
        if (from == null || to == null) {
            throw new InvalidBookingException("Both 'from' and 'to' dates are required");
        }
        if (from.isAfter(to)) {
            throw new InvalidBookingException("'from' date must be on or before 'to' date");
        }

        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found: " + adminId));

        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();
        List<Appointment> appointments = appointmentRepository.findBetweenOrderByStartTimeAsc(start, end);

        Path dir = Paths.get(exportDir);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create exports directory: " + dir, e);
        }

        boolean isText = "txt".equalsIgnoreCase(format) || "text".equalsIgnoreCase(format);
        String timestamp = LocalDateTime.now().format(FILE_TIMESTAMP);
        String savedFileName = "appointments-report-" + from + "-to-" + to + "_" + timestamp + (isText ? ".txt" : ".csv");
        Path filePath = dir.resolve(savedFileName);

        try (BufferedWriter writer = Files.newBufferedWriter(filePath, StandardCharsets.UTF_8)) {
            if (isText) {
                writePlainText(writer, appointments, from, to);
            } else {
                writeCsv(writer, appointments);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write report file: " + filePath, e);
        }

        byte[] bytes;
        try {
            bytes = Files.readAllBytes(filePath);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read report file: " + filePath, e);
        }

        auditService.record(admin, AuditAction.EXPORT, AuditEntityType.REPORT, 0L,
                "Exported appointments report (" + (isText ? "txt" : "csv") + ") from " + from + " to " + to);
        log.info("Appointments report exported by admin {} to {}", adminId, filePath);

        String mediaType = isText ? "text/plain; charset=UTF-8" : "text/csv; charset=UTF-8";
        String downloadName = isText ? "appointments-report.txt" : "appointments-report.csv";
        return new ExportResult(bytes, mediaType, downloadName);
    }

    private void writeCsv(BufferedWriter writer, List<Appointment> appointments) throws IOException {
        writer.write("Appointment ID,Doctor,Specialization,Patient,Start Time,End Time,Status,Reason");
        writer.newLine();
        for (Appointment a : appointments) {
            writer.write(String.join(",",
                    String.valueOf(a.getId()),
                    escapeCsv(a.getDoctor().getDisplayName()),
                    escapeCsv(a.getDoctor().getSpecialization()),
                    escapeCsv(a.getPatient().getFullName()),
                    escapeCsv(a.getStartTime().format(DATE_TIME_FORMATTER)),
                    escapeCsv(a.getEndTime().format(DATE_TIME_FORMATTER)),
                    escapeCsv(a.getStatus().name()),
                    escapeCsv(a.getReason() != null ? a.getReason() : "")
            ));
            writer.newLine();
        }
    }

    private void writePlainText(BufferedWriter writer, List<Appointment> appointments, LocalDate from, LocalDate to) throws IOException {
        writer.write("CLINIC APPOINTMENT REPORT (" + from + " to " + to + ")");
        writer.newLine();
        writer.write("Total Appointments: " + appointments.size());
        writer.newLine();
        writer.write("================================================================================");
        writer.newLine();
        for (Appointment a : appointments) {
            writer.write(String.format("[%d] %s - %s | Doctor: %s (%s) | Patient: %s | Status: %s | Reason: %s",
                    a.getId(),
                    a.getStartTime().format(DATE_TIME_FORMATTER),
                    a.getEndTime().format(DATE_TIME_FORMATTER),
                    a.getDoctor().getDisplayName(),
                    a.getDoctor().getSpecialization(),
                    a.getPatient().getFullName(),
                    a.getStatus(),
                    a.getReason() != null ? a.getReason() : "None"));
            writer.newLine();
        }
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
