package com.clinic.ehr.integration;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

/** Full stack (security filters + controllers + services + MySQL). Run with: mvn test -Pmysql-tests */
@Tag("mysql")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthSecurityIntegrationTest {

    private static final String PASSWORD = "Passw0rd1";

    @Autowired MockMvc mvc;

    private static String registerJson(String email, String extra) {
        return """
               {"fullName":"Nina Patient","email":"%s","password":"%s","phone":"9876543210",
                "dateOfBirth":"1995-03-04","gender":"FEMALE"%s}
               """.formatted(email, PASSWORD, extra);
    }

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    private void register(String email) throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(registerJson(email, "")))
                .andExpect(status().isCreated());
    }

    private MockHttpSession login(String email, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    @Test
    void registrationAlwaysCreatesPatientAndNeverExposesPasswordHash() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(uniqueEmail(), ",\"role\":\"ADMIN\"")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("PATIENT"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void duplicateEmailIs409() throws Exception {
        String email = uniqueEmail();
        register(email);
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(registerJson(email, "")))
                .andExpect(status().isConflict());
    }

    @Test
    void invalidRegistrationReturnsFieldErrors() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"\",\"email\":\"bad\",\"password\":\"x\",\"dateOfBirth\":\"2999-01-01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors").isNotEmpty());
    }

    @Test
    void loginCreatesSessionAndMeReturnsTheUser() throws Exception {
        String email = uniqueEmail();
        register(email);
        MockHttpSession session = login(email, PASSWORD);
        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void loginResponseCarriesTheXsrfTokenCookie() throws Exception {
        String email = uniqueEmail();
        register(email);
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("XSRF-TOKEN"));
    }

    @Test
    void wrongPasswordIs401AndAnonymousAccessIs401() throws Exception {
        String email = uniqueEmail();
        register(email);
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"WrongPass1\"}".formatted(email)))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void patientCannotUseAdminEndpoints() throws Exception {
        String email = uniqueEmail();
        register(email);
        MockHttpSession session = login(email, PASSWORD);
        mvc.perform(get("/api/admin/audit-logs").session(session)).andExpect(status().isForbidden());
    }

    @Test
    void adminCreatesDoctorWhoCanThenLoginButNotUseAdminEndpoints() throws Exception {
        MockHttpSession admin = login("admin@test.local", "Admin12345");
        String doctorEmail = uniqueEmail();
        String body = """
                {"fullName":"Dan Doctor","email":"%s","password":"%s","specialization":"Cardiology",
                 "licenseNumber":"LIC-%s","slotMinutes":20}
                """.formatted(doctorEmail, PASSWORD, UUID.randomUUID());

        mvc.perform(post("/api/admin/doctors").session(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("DOCTOR"))
                .andExpect(jsonPath("$.displayName").value("Dr. Dan Doctor"));

        mvc.perform(get("/api/admin/audit-logs").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].action").value("CREATE"));

        MockHttpSession doctor = login(doctorEmail, PASSWORD);
        mvc.perform(get("/api/admin/audit-logs").session(doctor)).andExpect(status().isForbidden());
    }

    @Test
    void stateChangingCallWithoutCsrfTokenIsRejected() throws Exception {
        MockHttpSession admin = login("admin@test.local", "Admin12345");
        mvc.perform(post("/api/admin/doctors").session(admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void logoutEndsTheSession() throws Exception {
        String email = uniqueEmail();
        register(email);
        MockHttpSession session = login(email, PASSWORD);
        mvc.perform(post("/api/auth/logout").session(session).with(csrf())).andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
    }
}
