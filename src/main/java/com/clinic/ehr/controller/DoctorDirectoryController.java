package com.clinic.ehr.controller;

import com.clinic.ehr.dto.DoctorResponse;
import com.clinic.ehr.dto.SlotResponse;
import com.clinic.ehr.service.DoctorService;
import com.clinic.ehr.service.SlotGenerationService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Doctor browsing. The list and profile are public; slots need a signed-in user (see SecurityConfig). */
@RestController
@RequestMapping("/api/doctors")
public class DoctorDirectoryController {

    private final DoctorService doctorService;
    private final SlotGenerationService slotGenerationService;

    public DoctorDirectoryController(DoctorService doctorService, SlotGenerationService slotGenerationService) {
        this.doctorService = doctorService;
        this.slotGenerationService = slotGenerationService;
    }

    @GetMapping
    public List<DoctorResponse> search(@RequestParam(required = false) String specialization,
                                       @RequestParam(required = false) String name) {
        return doctorService.search(specialization, name);
    }

    @GetMapping("/{doctorId}")
    public DoctorResponse get(@PathVariable Long doctorId) {
        return doctorService.getActiveDoctor(doctorId);
    }

    @GetMapping("/{doctorId}/slots")
    public List<SlotResponse> slots(@PathVariable Long doctorId,
                                    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return slotGenerationService.availableSlots(doctorId, date);
    }
}
