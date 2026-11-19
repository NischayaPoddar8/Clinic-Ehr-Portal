package com.clinic.ehr.service.booking;

import static com.clinic.ehr.support.TestData.NEXT_MONDAY;
import static com.clinic.ehr.support.TestData.doctor;
import static com.clinic.ehr.support.TestData.patient;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.clinic.ehr.exception.AppointmentConflictException;
import com.clinic.ehr.repository.AppointmentRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class PatientSlotConflictRuleTest {

    private static final LocalDateTime START = NEXT_MONDAY.atTime(10, 0);
    private final AppointmentRepository repository = mock(AppointmentRepository.class);
    private final PatientSlotConflictRule rule = new PatientSlotConflictRule(repository);

    @Test
    void throwsAppointmentConflictWhenThePatientIsBusy() {
        when(repository.existsSlotConflictForPatient(2L, START, START.plusMinutes(30), null)).thenReturn(true);
        assertThatThrownBy(() -> rule.validate(new BookingContext(doctor(1L), patient(2L), START, START.plusMinutes(30), null)))
                .isInstanceOf(AppointmentConflictException.class);
    }

    @Test
    void passesWhenFreeAndForwardsTheIgnoredId() {
        assertThatCode(() -> rule.validate(new BookingContext(doctor(1L), patient(2L), START, START.plusMinutes(30), 5L)))
                .doesNotThrowAnyException();
        verify(repository).existsSlotConflictForPatient(2L, START, START.plusMinutes(30), 5L);
    }
}
