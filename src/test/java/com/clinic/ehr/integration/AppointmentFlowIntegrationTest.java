package com.clinic.ehr.integration;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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

/** Doctor discovery, availability, slots and the appointment lifecycle over HTTP + CSRF + MySQL. Run with -Pmysql-tests. */
@Tag("mysql")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AppointmentFlowIntegrationTest {

    private static final String PASSWORD = "Passw0rd1";

    @Autowired MockMvc mvc;

    /** First Monday at least a week ahead, so every slot is in the future. */
    private static LocalDate monday() {
        LocalDate day = LocalDate.now().plusDays(7);
        while (day.getDayOfWeek() != DayOfWeek.MONDAY) {
            day = day.plusDays(1);
        }
        return day;
    }

    private record Account(long id, String email, String specialization, MockHttpSession session) { }

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
        MvcResult result = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Pat Patient","email":"%s","password":"%s","dateOfBirth":"1990-05-05","gender":"MALE"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isCreated()).andReturn();
        return new Account(idOf(result), email, null, login(email));
    }

    private Account newDoctor() throws Exception {
        MockHttpSession admin = login("admin@test.local", "Admin12345");
        String email = unique("doctor") + "@example.com";
        String specialization = unique("Spec");
        MvcResult result = mvc.perform(post("/api/admin/doctors").session(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Dan Doctor","email":"%s","password":"%s","specialization":"%s","licenseNumber":"%s"}
                                """.formatted(email, PASSWORD, specialization, unique("LIC"))))
                .andExpect(status().isCreated()).andReturn();
        return new Account(idOf(result), email, specialization, login(email));
    }

    private long addWindow(Account doctor, String from, String to) throws Exception {
        MvcResult result = mvc.perform(post("/api/doctor/availability").session(doctor.session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dayOfWeek\":\"MONDAY\",\"startTime\":\"%s\",\"endTime\":\"%s\"}".formatted(from, to)))
                .andExpect(status().isCreated()).andReturn();
        return idOf(result);
    }

    private MvcResult book(Account patient, Account doctor, String time, int expectedStatus) throws Exception {
        return mvc.perform(post("/api/patient/appointments").session(patient.session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"doctorId\":%d,\"startTime\":\"%sT%s\",\"reason\":\"checkup\"}"
                                .formatted(doctor.id(), monday(), time)))
                .andExpect(status().is(expectedStatus)).andReturn();
    }

    private void expectSlotCount(Account viewer, Account doctor, int count) throws Exception {
        mvc.perform(get("/api/doctors/{id}/slots", doctor.id()).param("date", monday().toString()).session(viewer.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(count)));
    }

    @Test
    void doctorBrowsingIsPublicButSlotsNeedLogin() throws Exception {
        Account doctor = newDoctor();
        mvc.perform(get("/api/doctors").param("specialization", doctor.specialization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(doctor.id()))
                .andExpect(jsonPath("$[0].displayName").value("Dr. Dan Doctor"))
                .andExpect(jsonPath("$[0].email").doesNotExist());
        mvc.perform(get("/api/doctors/{id}", doctor.id())).andExpect(status().isOk());
        mvc.perform(get("/api/doctors/{id}", 999_999_999L)).andExpect(status().isNotFound());
        mvc.perform(get("/api/doctors/{id}/slots", doctor.id()).param("date", monday().toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void fullAppointmentLifecycle() throws Exception {
        Account doctor = newDoctor();
        addWindow(doctor, "09:00", "12:00");
        Account first = newPatient();
        Account second = newPatient();

        expectSlotCount(first, doctor, 6);
        MvcResult booked = book(first, doctor, "09:00", 201);
        long appointmentId = idOf(booked);
        expectSlotCount(first, doctor, 5);

        book(second, doctor, "09:00", 409);                               // slot taken
        book(first, doctor, "09:10", 400);                                // not on the slot grid
        mvc.perform(post("/api/patient/appointments/{id}/cancel", appointmentId)
                        .session(second.session()).with(csrf()))
                .andExpect(status().isForbidden());                       // not the owner

        mvc.perform(post("/api/patient/appointments/{id}/cancel", appointmentId)
                        .session(first.session()).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        expectSlotCount(first, doctor, 6);                                // cancelled slot is free again

        long secondId = idOf(book(second, doctor, "09:00", 201));        // and re-bookable
        mvc.perform(put("/api/patient/appointments/{id}/reschedule", secondId).session(second.session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newStartTime\":\"%sT10:00\"}".formatted(monday())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startTime").value(monday() + "T10:00:00"));

        mvc.perform(get("/api/patient/appointments").session(second.session()).param("status", "BOOKED"))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(get("/api/doctor/appointments").session(doctor.session()).param("date", monday().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)));

        mvc.perform(post("/api/doctor/appointments/{id}/cancel", secondId).session(doctor.session()).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void patientCannotBookTwoOverlappingAppointments() throws Exception {
        Account doctorA = newDoctor();
        Account doctorB = newDoctor();
        addWindow(doctorA, "09:00", "12:00");
        addWindow(doctorB, "09:00", "12:00");
        Account patient = newPatient();
        book(patient, doctorA, "09:00", 201);
        book(patient, doctorB, "09:00", 409);
    }

    @Test
    void pastDatesAndPastTimesAreRejected() throws Exception {
        Account doctor = newDoctor();
        Account patient = newPatient();
        mvc.perform(get("/api/doctors/{id}/slots", doctor.id())
                        .param("date", LocalDate.now().minusDays(1).toString()).session(patient.session()))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/patient/appointments").session(patient.session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"doctorId\":%d,\"startTime\":\"%sT09:00\"}".formatted(doctor.id(), LocalDate.now().minusDays(1))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void bookingWithoutCsrfTokenIsForbidden() throws Exception {
        Account doctor = newDoctor();
        Account patient = newPatient();
        mvc.perform(post("/api/patient/appointments").session(patient.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"doctorId\":%d,\"startTime\":\"%sT09:00\"}".formatted(doctor.id(), monday())))
                .andExpect(status().isForbidden());
    }

    @Test
    void roleSeparationBetweenDoctorAndPatientEndpoints() throws Exception {
        Account doctor = newDoctor();
        Account patient = newPatient();
        mvc.perform(get("/api/doctor/appointments").session(patient.session())).andExpect(status().isForbidden());
        mvc.perform(get("/api/patient/appointments").session(doctor.session())).andExpect(status().isForbidden());
        mvc.perform(get("/api/patient/appointments")).andExpect(status().isUnauthorized());
    }

    @Test
    void availabilityRulesAndOwnership() throws Exception {
        Account doctor = newDoctor();
        Account other = newDoctor();
        long windowId = addWindow(doctor, "09:00", "12:00");

        mvc.perform(post("/api/doctor/availability").session(doctor.session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dayOfWeek\":\"MONDAY\",\"startTime\":\"11:00\",\"endTime\":\"13:00\"}"))
                .andExpect(status().isConflict());                        // overlaps 09:00-12:00
        mvc.perform(post("/api/doctor/availability").session(doctor.session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dayOfWeek\":\"TUESDAY\",\"startTime\":\"09:00\",\"endTime\":\"09:10\"}"))
                .andExpect(status().isBadRequest());                      // shorter than one 30-minute slot
        mvc.perform(get("/api/doctor/availability").session(doctor.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));

        mvc.perform(delete("/api/doctor/availability/{id}", windowId).session(other.session()).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/doctor/availability/{id}", windowId).session(doctor.session()).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void doctorUpdatesOwnProfileAndSlotLengthChangesSlots() throws Exception {
        Account doctor = newDoctor();
        Account patient = newPatient();
        mvc.perform(put("/api/doctor/profile").session(doctor.session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Dana Doc\",\"specialization\":\"Dermatology\",\"slotMinutes\":20}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Dr. Dana Doc"))
                .andExpect(jsonPath("$.slotMinutes").value(20));
        addWindow(doctor, "09:00", "10:00");
        expectSlotCount(patient, doctor, 3);
    }
}

