package com.clinic.ehr.dto;

public record ClinicStatsResponse(
        long activePatients,
        long inactivePatients,
        long activeDoctors,
        long inactiveDoctors,
        long bookedAppointments,
        long completedAppointments,
        long cancelledAppointments,
        long totalMedicalRecords,
        long totalPrescriptions,
        long todayAppointments) {
}
