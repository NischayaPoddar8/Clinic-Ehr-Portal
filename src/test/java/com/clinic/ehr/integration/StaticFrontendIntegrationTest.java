package com.clinic.ehr.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@Tag("mysql")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StaticFrontendIntegrationTest {

    @Autowired MockMvc mvc;

    @ParameterizedTest
    @ValueSource(strings = {
        "/index.html",
        "/login.html",
        "/register.html",
        "/patient.html",
        "/doctor.html",
        "/admin.html",
        "/css/styles.css",
        "/js/api.js",
        "/js/auth.js",
        "/js/patient.js",
        "/js/doctor.js",
        "/js/admin.js"
    })
    void staticFrontendAssetsAreServed(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isOk());
    }
}