package com.clinic.ehr.integration;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Phase 6 Integration Tests:
 * Admin User Management, Appointment Oversight, Clinic Statistics Dashboard,
 * and Report Exporter over HTTP + CSRF + MySQL.
 * Run with: mvn test -Pmysql-tests
 */
@Tag("mysql")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminOversightIntegrationTest {

    private static final String PASSWORD = "Passw0rd1";

    @Autowired MockMvc mvc;

    private static LocalDate nextMonday() {
        LocalDate day = LocalDate.now().plusDays(7);
        while (day.getDayOfWeek() != DayOfWeek.MONDAY) {
            day = day.plusDays(1);
        }
        return day;
    }

    private record Account(long id, String email, String name, MockHttpSession session) { }

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private static long idOf(MvcResult result) throws Exception {
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private MockHttpSession login(String email) throws Exception {
        return login(email, PASSWORD);
    }

    private MockHttpSession login(String email, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private Account adminAccount() throws Exception {
        MockHttpSession admin = login("admin@test.local", "Admin12345");
        MvcResult me = mvc.perform(get("/api/auth/me").session(admin)).andExpect(status().isOk()).andReturn();
        return new Account(idOf(me), "admin@test.local", "Root Admin", admin);
    }

    private Account newPatient() throws Exception {
        String email = unique("patient") + "@example.com";
        String name = "Patient " + UUID.randomUUID().toString().substring(0, 5);
        MvcResult result = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"%s","email":"%s","password":"%s","dateOfBirth":"1995-04-12","gender":"MALE"}
                                """.formatted(name, email, PASSWORD)))
                .andExpect(status().isCreated()).andReturn();
        return new Account(idOf(result), email, name, login(email));
    }

    private Account newDoctor() throws Exception {
        MockHttpSession admin = login("admin@test.local", "Admin12345");
        String email = unique("doctor") + "@example.com";
        String name = "Doctor " + UUID.randomUUID().toString().substring(0, 5);
        String specialization = unique("Spec");
        MvcResult result = mvc.perform(post("/api/admin/doctors").session(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"%s","email":"%s","password":"%s","specialization":"%s","licenseNumber":"%s"}
                                """.formatted(name, email, PASSWORD, specialization, unique("LIC"))))
                .andExpect(status().isCreated()).andReturn();
        return new Account(idOf(result), email, name, login(email));
    }

    private void addAvailability(Account doctor, String from, String to) throws Exception {
        mvc.perform(post("/api/doctor/availability").session(doctor.session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dayOfWeek\":\"MONDAY\",\"startTime\":\"%s\",\"endTime\":\"%s\"}".formatted(from, to)))
                .andExpect(status().isCreated());
    }

    private long bookAppointment(Account patient, Account doctor, String time) throws Exception {
        MvcResult result = mvc.perform(post("/api/patient/appointments").session(patient.session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"doctorId\":%d,\"startTime\":\"%sT%s\",\"reason\":\"Consultation\"}"
                                .formatted(doctor.id(), nextMonday(), time)))
                .andExpect(status().isCreated()).andReturn();
        return idOf(result);
    }

    @Test
    void adminUserManagementFlow() throws Exception {
        Account admin = adminAccount();
        Account patient = newPatient();

        // 1. Non-admin cannot access admin users endpoint (403)
        mvc.perform(get("/api/admin/users").session(patient.session()))
                .andExpect(status().isForbidden());

        // 2. Admin lists users with filtering
        mvc.perform(get("/api/admin/users").param("role", "PATIENT").session(admin.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));

        // 3. Admin deactivates patient
        mvc.perform(patch("/api/admin/users/{id}/status", patient.id()).session(admin.session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(patient.id()))
                .andExpect(jsonPath("$.active").value(false));

        // 4. Deactivated user cannot log in (401)
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(patient.email(), PASSWORD)))
                .andExpect(status().isUnauthorized());

        // 5. Admin reactivates patient
        mvc.perform(patch("/api/admin/users/{id}/status", patient.id()).session(admin.session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));

        // 6. Reactivated user can log in again
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(patient.email(), PASSWORD)))
                .andExpect(status().isOk());

        // 7. Admin cannot deactivate their own account (400)
        mvc.perform(patch("/api/admin/users/{id}/status", admin.id()).session(admin.session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminAppointmentOversightAndCancellation() throws Exception {
        Account admin = adminAccount();
        Account doctor = newDoctor();
        Account patient = newPatient();
        addAvailability(doctor, "09:00", "12:00");
        long appointmentId = bookAppointment(patient, doctor, "09:00");

        // 1. Non-admin cannot access admin appointment list
        mvc.perform(get("/api/admin/appointments").session(patient.session()))
                .andExpect(status().isForbidden());

        // 2. Admin lists appointments with doctor/patient filters
        mvc.perform(get("/api/admin/appointments")
                        .param("doctorId", String.valueOf(doctor.id()))
                        .param("status", "BOOKED")
                        .session(admin.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(appointmentId))
                .andExpect(jsonPath("$[0].patientName").value(patient.name()));

        // 3. Admin cancels appointment
        mvc.perform(post("/api/admin/appointments/{id}/cancel", appointmentId)
                        .session(admin.session()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(appointmentId))
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        // 4. Cancelling an already cancelled appointment fails (400)
        mvc.perform(post("/api/admin/appointments/{id}/cancel", appointmentId)
                        .session(admin.session()).with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void clinicStatisticsDashboard() throws Exception {
        Account admin = adminAccount();
        Account doctor = newDoctor();
        Account patient = newPatient();

        // Non-admin cannot view stats (403)
        mvc.perform(get("/api/admin/stats").session(patient.session()))
                .andExpect(status().isForbidden());

        // Admin views stats
        mvc.perform(get("/api/admin/stats").session(admin.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activePatients", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.activeDoctors", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.bookedAppointments", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.completedAppointments", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.cancelledAppointments", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.totalMedicalRecords", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.totalPrescriptions", greaterThanOrEqualTo(0)));
    }

    @Test
    void reportExporterFlow() throws Exception {
        Account admin = adminAccount();
        Account doctor = newDoctor();
        Account patient = newPatient();
        addAvailability(doctor, "09:00", "12:00");
        bookAppointment(patient, doctor, "09:00");

        LocalDate from = nextMonday().minusDays(1);
        LocalDate to = nextMonday().plusDays(1);

        // 1. Non-admin cannot export report (403)
        mvc.perform(get("/api/admin/reports/appointments")
                        .param("from", from.toString())
                        .param("to", to.toString())
                        .session(patient.session()))
                .andExpect(status().isForbidden());

        // 2. Invalid date range (400)
        mvc.perform(get("/api/admin/reports/appointments")
                        .param("from", to.toString())
                        .param("to", from.toString())
                        .session(admin.session()))
                .andExpect(status().isBadRequest());

        // 3. Export CSV report
        mvc.perform(get("/api/admin/reports/appointments")
                        .param("from", from.toString())
                        .param("to", to.toString())
                        .param("format", "csv")
                        .session(admin.session()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"appointments-report.csv\""))
                .andExpect(content().contentType("text/csv; charset=UTF-8"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Appointment ID,Doctor,Specialization,Patient,Start Time,End Time,Status,Reason")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(patient.name())));

        // 4. Export TXT report
        mvc.perform(get("/api/admin/reports/appointments")
                        .param("from", from.toString())
                        .param("to", to.toString())
                        .param("format", "txt")
                        .session(admin.session()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"appointments-report.txt\""))
                .andExpect(content().contentType("text/plain; charset=UTF-8"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("CLINIC APPOINTMENT REPORT")));

        // 5. Verify audit log entry for EXPORT
        mvc.perform(get("/api/admin/audit-logs").session(admin.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.action == 'EXPORT' && @.entityType == 'REPORT')]").exists());
    }
}
