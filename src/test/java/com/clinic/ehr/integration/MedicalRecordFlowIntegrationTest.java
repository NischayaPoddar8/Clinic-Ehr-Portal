package com.clinic.ehr.integration;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * End-to-end integration tests for Phase 5:
 * Appointment completion, Medical Records and Prescriptions management, Role-based access control,
 * and Audit logging over HTTP + CSRF + MySQL.
 * Run with: mvn test -Pmysql-tests
 */
@Tag("mysql")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MedicalRecordFlowIntegrationTest {

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

    private Account newPatient() throws Exception {
        String email = unique("patient") + "@example.com";
        String name = "Patient " + UUID.randomUUID().toString().substring(0, 5);
        MvcResult result = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"%s","email":"%s","password":"%s","dateOfBirth":"1992-06-15","gender":"FEMALE"}
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
                        .content("{\"doctorId\":%d,\"startTime\":\"%sT%s\",\"reason\":\"Regular checkup\"}"
                                .formatted(doctor.id(), nextMonday(), time)))
                .andExpect(status().isCreated()).andReturn();
        return idOf(result);
    }

    @Test
    void appointmentCompletionFlow() throws Exception {
        Account doctor = newDoctor();
        Account otherDoctor = newDoctor();
        Account patient = newPatient();
        addAvailability(doctor, "09:00", "12:00");
        long appointmentId = bookAppointment(patient, doctor, "09:00");

        // Strangers and patients cannot complete the appointment
        mvc.perform(post("/api/doctor/appointments/{id}/complete", appointmentId)
                        .session(patient.session()).with(csrf()))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/doctor/appointments/{id}/complete", appointmentId)
                        .session(otherDoctor.session()).with(csrf()))
                .andExpect(status().isForbidden());

        // Assigned doctor successfully completes the appointment
        mvc.perform(post("/api/doctor/appointments/{id}/complete", appointmentId)
                        .session(doctor.session()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(appointmentId))
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        // Already completed appointment cannot be completed again (400)
        mvc.perform(post("/api/doctor/appointments/{id}/complete", appointmentId)
                        .session(doctor.session()).with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cancelledAppointmentCannotBeCompleted() throws Exception {
        Account doctor = newDoctor();
        Account patient = newPatient();
        addAvailability(doctor, "09:00", "12:00");
        long appointmentId = bookAppointment(patient, doctor, "09:00");

        mvc.perform(post("/api/patient/appointments/{id}/cancel", appointmentId)
                        .session(patient.session()).with(csrf()))
                .andExpect(status().isOk());

        mvc.perform(post("/api/doctor/appointments/{id}/complete", appointmentId)
                        .session(doctor.session()).with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void fullMedicalRecordAndPrescriptionLifecycle() throws Exception {
        Account doctor = newDoctor();
        Account otherDoctor = newDoctor();
        Account patient = newPatient();
        Account otherPatient = newPatient();

        addAvailability(doctor, "09:00", "12:00");
        long appointmentId = bookAppointment(patient, doctor, "09:00");

        // 1. Cannot create medical record before appointment is completed (400 InvalidBooking)
        mvc.perform(post("/api/doctor/appointments/{id}/medical-record", appointmentId)
                        .session(doctor.session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"diagnosis":"Hypertension","notes":"Patient reports dizziness"}
                                """))
                .andExpect(status().isBadRequest());

        // Doctor completes the appointment
        mvc.perform(post("/api/doctor/appointments/{id}/complete", appointmentId)
                        .session(doctor.session()).with(csrf()))
                .andExpect(status().isOk());

        // 2. Other doctor cannot create medical record for this appointment (403 UnauthorizedAccess)
        mvc.perform(post("/api/doctor/appointments/{id}/medical-record", appointmentId)
                        .session(otherDoctor.session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"diagnosis":"Hypertension","notes":"Patient reports dizziness"}
                                """))
                .andExpect(status().isForbidden());

        // 3. Assigned doctor creates medical record with nested prescriptions (201 Created)
        MvcResult recordResult = mvc.perform(post("/api/doctor/appointments/{id}/medical-record", appointmentId)
                        .session(doctor.session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "diagnosis":"Essential Hypertension",
                                    "notes":"Blood pressure 145/95. Advised low-sodium diet and exercise.",
                                    "prescriptions":[
                                        {
                                            "medication":"Lisinopril",
                                            "dosage":"10mg",
                                            "frequency":"Once daily",
                                            "durationDays":30,
                                            "instructions":"Take in the morning with water"
                                        },
                                        {
                                            "medication":"Hydrochlorothiazide",
                                            "dosage":"12.5mg",
                                            "frequency":"Once daily",
                                            "durationDays":30,
                                            "instructions":"Take with food"
                                        }
                                    ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.appointmentId").value(appointmentId))
                .andExpect(jsonPath("$.patientId").value(patient.id()))
                .andExpect(jsonPath("$.doctorId").value(doctor.id()))
                .andExpect(jsonPath("$.diagnosis").value("Essential Hypertension"))
                .andExpect(jsonPath("$.prescriptions", hasSize(2)))
                .andExpect(jsonPath("$.prescriptions[0].medication").value("Lisinopril"))
                .andExpect(jsonPath("$.prescriptions[0].durationDays").value(30))
                .andExpect(jsonPath("$.prescriptions[1].medication").value("Hydrochlorothiazide"))
                .andReturn();

        long recordId = idOf(recordResult);

        // 4. Cannot create a second medical record for the same appointment (409 DuplicateResource)
        mvc.perform(post("/api/doctor/appointments/{id}/medical-record", appointmentId)
                        .session(doctor.session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"diagnosis":"Duplicate","notes":"Should fail"}
                                """))
                .andExpect(status().isConflict());

        // 5. Doctor reads record by ID
        mvc.perform(get("/api/doctor/medical-records/{id}", recordId)
                        .session(doctor.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(recordId))
                .andExpect(jsonPath("$.diagnosis").value("Essential Hypertension"))
                .andExpect(jsonPath("$.prescriptions", hasSize(2)));

        // Other doctor cannot read record by ID (403)
        mvc.perform(get("/api/doctor/medical-records/{id}", recordId)
                        .session(otherDoctor.session()))
                .andExpect(status().isForbidden());

        // 6. Doctor updates record and syncs prescriptions
        mvc.perform(put("/api/doctor/medical-records/{id}", recordId)
                        .session(doctor.session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "diagnosis":"Stage 1 Hypertension - Controlled",
                                    "notes":"Follow-up noted significant improvement in BP readings.",
                                    "prescriptions":[
                                        {
                                            "medication":"Lisinopril",
                                            "dosage":"5mg",
                                            "frequency":"Once daily",
                                            "durationDays":60,
                                            "instructions":"Reduced dosage"
                                        }
                                    ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.diagnosis").value("Stage 1 Hypertension - Controlled"))
                .andExpect(jsonPath("$.prescriptions", hasSize(1)))
                .andExpect(jsonPath("$.prescriptions[0].medication").value("Lisinopril"))
                .andExpect(jsonPath("$.prescriptions[0].dosage").value("5mg"))
                .andExpect(jsonPath("$.prescriptions[0].durationDays").value(60));

        // Other doctor cannot update record (403)
        mvc.perform(put("/api/doctor/medical-records/{id}", recordId)
                        .session(otherDoctor.session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"diagnosis\":\"Hacked\",\"notes\":\"Bad\"}"))
                .andExpect(status().isForbidden());

        // 7. Doctor views medical records of this patient
        mvc.perform(get("/api/doctor/patients/{patientId}/medical-records", patient.id())
                        .session(doctor.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(recordId))
                .andExpect(jsonPath("$[0].diagnosis").value("Stage 1 Hypertension - Controlled"));

        // Other doctor has no appointments with this patient -> empty list
        mvc.perform(get("/api/doctor/patients/{patientId}/medical-records", patient.id())
                        .session(otherDoctor.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        // 8. Patient views own medical records
        mvc.perform(get("/api/patient/medical-records")
                        .session(patient.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(recordId))
                .andExpect(jsonPath("$[0].doctorName").value("Dr. " + doctor.name()))
                .andExpect(jsonPath("$[0].prescriptions", hasSize(1)));

        // Patient views single record by ID
        mvc.perform(get("/api/patient/medical-records/{id}", recordId)
                        .session(patient.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(recordId))
                .andExpect(jsonPath("$.diagnosis").value("Stage 1 Hypertension - Controlled"));

        // Other patient cannot view record (403)
        mvc.perform(get("/api/patient/medical-records/{id}", recordId)
                        .session(otherPatient.session()))
                .andExpect(status().isForbidden());

        // 9. Audit trail verification by Admin
        MockHttpSession admin = login("admin@test.local", "Admin12345");
        mvc.perform(get("/api/admin/audit-logs").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.action == 'COMPLETE' && @.entityType == 'APPOINTMENT')]").exists())
                .andExpect(jsonPath("$[?(@.action == 'CREATE' && @.entityType == 'MEDICAL_RECORD')]").exists())
                .andExpect(jsonPath("$[?(@.action == 'UPDATE' && @.entityType == 'MEDICAL_RECORD')]").exists());
    }

    @Test
    void medicalRecordValidationRejections() throws Exception {
        Account doctor = newDoctor();
        Account patient = newPatient();
        addAvailability(doctor, "09:00", "12:00");
        long appointmentId = bookAppointment(patient, doctor, "09:00");

        mvc.perform(post("/api/doctor/appointments/{id}/complete", appointmentId)
                        .session(doctor.session()).with(csrf()))
                .andExpect(status().isOk());

        // Blank diagnosis is rejected
        mvc.perform(post("/api/doctor/appointments/{id}/medical-record", appointmentId)
                        .session(doctor.session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"diagnosis\":\"   \",\"notes\":\"test\"}"))
                .andExpect(status().isBadRequest());

        // Invalid prescription: durationDays <= 0
        mvc.perform(post("/api/doctor/appointments/{id}/medical-record", appointmentId)
                        .session(doctor.session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "diagnosis":"Valid diagnosis",
                                    "prescriptions":[
                                        {"medication":"Aspirin","dosage":"100mg","frequency":"daily","durationDays":0}
                                    ]
                                }
                                """))
                .andExpect(status().isBadRequest());

        // Invalid prescription: blank medication
        mvc.perform(post("/api/doctor/appointments/{id}/medical-record", appointmentId)
                        .session(doctor.session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "diagnosis":"Valid diagnosis",
                                    "prescriptions":[
                                        {"medication":"","dosage":"100mg","frequency":"daily","durationDays":5}
                                    ]
                                }
                                """))
                .andExpect(status().isBadRequest());
    }
}
