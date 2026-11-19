package com.clinic.ehr.service;

import static com.clinic.ehr.support.TestData.NEXT_MONDAY;
import static com.clinic.ehr.support.TestData.doctor;
import static com.clinic.ehr.support.TestData.patient;
import static com.clinic.ehr.support.TestData.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.clinic.ehr.dto.AppointmentResponse;
import com.clinic.ehr.dto.BookAppointmentRequest;
import com.clinic.ehr.dto.RescheduleRequest;
import com.clinic.ehr.exception.AppointmentConflictException;
import com.clinic.ehr.exception.InvalidBookingException;
import com.clinic.ehr.exception.ResourceNotFoundException;
import com.clinic.ehr.exception.SlotAlreadyBookedException;
import com.clinic.ehr.exception.UnauthorizedAccessException;
import com.clinic.ehr.model.Appointment;
import com.clinic.ehr.model.Doctor;
import com.clinic.ehr.model.Patient;
import com.clinic.ehr.model.User;
import com.clinic.ehr.model.enums.AppointmentStatus;
import com.clinic.ehr.model.enums.AuditAction;
import com.clinic.ehr.model.enums.AuditEntityType;
import com.clinic.ehr.model.enums.Role;
import com.clinic.ehr.repository.AppointmentRepository;
import com.clinic.ehr.repository.DoctorRepository;
import com.clinic.ehr.repository.PatientRepository;
import com.clinic.ehr.repository.UserRepository;
import com.clinic.ehr.service.booking.BookingContext;
import com.clinic.ehr.service.booking.BookingRule;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

class AppointmentServiceTest {

    private static final LocalDateTime NINE = NEXT_MONDAY.atTime(9, 0);
    private static final LocalDateTime TEN = NEXT_MONDAY.atTime(10, 0);

    private AppointmentRepository appointmentRepository;
    private DoctorRepository doctorRepository;
    private PatientRepository patientRepository;
    private UserRepository userRepository;
    private AuditService auditService;
    private final List<BookingContext> seen = new ArrayList<>();
    private final List<String> ruleOrder = new ArrayList<>();
    private BookingRule failure; // set by a test to make the second rule fail
    private AppointmentService service;
    private Doctor doctor;
    private Patient patient;
    private User admin;

    @BeforeEach
    void setUp() {
        appointmentRepository = mock(AppointmentRepository.class);
        doctorRepository = mock(DoctorRepository.class);
        patientRepository = mock(PatientRepository.class);
        userRepository = mock(UserRepository.class);
        auditService = mock(AuditService.class);
        BookingRule first = ctx -> { ruleOrder.add("first"); seen.add(ctx); };
        BookingRule second = ctx -> { ruleOrder.add("second"); if (failure != null) failure.validate(ctx); };
        service = new AppointmentService(appointmentRepository, doctorRepository, patientRepository, userRepository,
                auditService, List.of(first, second));

        doctor = doctor(5L);
        patient = patient(7L);
        admin = withId(new User("admin@clinic.com", "h", "Admin", null, Role.ADMIN), 1L);
        when(doctorRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(doctor));
        when(patientRepository.findById(7L)).thenReturn(Optional.of(patient));
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> withId(inv.getArgument(0), 100L));
    }

    private Appointment storedAppointment(LocalDateTime start) {
        Appointment a = withId(new Appointment(patient, doctor, start, "checkup"), 100L);
        when(appointmentRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(a));
        return a;
    }

    // ---- book
    @Test
    void bookSavesABookedAppointmentWithSlotKeyAfterLockingTheDoctor() {
        AppointmentResponse response = service.book(7L, new BookAppointmentRequest(5L, NINE, "checkup"));

        ArgumentCaptor<Appointment> saved = ArgumentCaptor.forClass(Appointment.class);
        verify(appointmentRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(AppointmentStatus.BOOKED);
        assertThat(saved.getValue().getSlotKey()).isEqualTo("5_203001210900");
        assertThat(saved.getValue().getEndTime()).isEqualTo(NINE.plusMinutes(30));
        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.doctorName()).isEqualTo("Dr. Dan Doctor");

        assertThat(ruleOrder).containsExactly("first", "second");
        assertThat(seen.get(0).ignoredAppointmentId()).isNull();
        assertThat(seen.get(0).end()).isEqualTo(NINE.plusMinutes(30));
        verify(auditService).record(same(patient), eq(AuditAction.BOOK), eq(AuditEntityType.APPOINTMENT), eq(100L), anyString());

        InOrder order = inOrder(doctorRepository, patientRepository);
        order.verify(doctorRepository).findByIdForUpdate(5L);
        order.verify(patientRepository).findById(7L);
    }

    @Test
    void bookRejectsUnknownInactiveDoctorAndUnknownPatient() {
        when(doctorRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.book(7L, new BookAppointmentRequest(99L, NINE, null)))
                .isInstanceOf(ResourceNotFoundException.class);

        doctor.setActive(false);
        assertThatThrownBy(() -> service.book(7L, new BookAppointmentRequest(5L, NINE, null)))
                .isInstanceOf(ResourceNotFoundException.class);

        doctor.setActive(true);
        when(patientRepository.findById(8L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.book(8L, new BookAppointmentRequest(5L, NINE, null)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(appointmentRepository, never()).save(any(Appointment.class));
    }

    @Test
    void aFailingRuleStopsTheBookingBeforeAnythingIsSaved() {
        failure = ctx -> { throw new SlotAlreadyBookedException("taken"); };
        assertThatThrownBy(() -> service.book(7L, new BookAppointmentRequest(5L, NINE, null)))
                .isInstanceOf(SlotAlreadyBookedException.class);
        verify(appointmentRepository, never()).save(any(Appointment.class));
        verify(auditService, never()).record(any(), any(), any(), any(), any());
    }

    // ---- cancel
    @Test
    void patientCancelFreesTheSlotKeyAndIsAudited() {
        Appointment a = storedAppointment(NINE);
        AppointmentResponse response = service.cancel(100L, 7L, Role.PATIENT);
        assertThat(a.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(a.getSlotKey()).isNull();
        assertThat(response.status()).isEqualTo(AppointmentStatus.CANCELLED);
        verify(auditService).record(same(patient), eq(AuditAction.CANCEL), eq(AuditEntityType.APPOINTMENT), eq(100L), anyString());
    }

    @Test
    void assignedDoctorMayCancelAndIsTheAuditActor() {
        Appointment a = storedAppointment(NINE);
        service.cancel(100L, 5L, Role.DOCTOR);
        assertThat(a.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        verify(auditService).record(same(doctor), eq(AuditAction.CANCEL), eq(AuditEntityType.APPOINTMENT), eq(100L), anyString());
    }

    @Test
    void strangersCannotCancel() {
        Appointment a = storedAppointment(NINE);
        assertThatThrownBy(() -> service.cancel(100L, 8L, Role.PATIENT)).isInstanceOf(UnauthorizedAccessException.class);
        assertThatThrownBy(() -> service.cancel(100L, 6L, Role.DOCTOR)).isInstanceOf(UnauthorizedAccessException.class);
        assertThatThrownBy(() -> service.cancel(100L, 1L, Role.ADMIN)).isInstanceOf(UnauthorizedAccessException.class);
        assertThat(a.getStatus()).isEqualTo(AppointmentStatus.BOOKED);
        assertThat(a.getSlotKey()).isNotNull();
        verify(auditService, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void cancellingTwiceOrUnknownFails() {
        storedAppointment(NINE);
        service.cancel(100L, 7L, Role.PATIENT);
        assertThatThrownBy(() -> service.cancel(100L, 7L, Role.PATIENT)).isInstanceOf(IllegalStateException.class);
        when(appointmentRepository.findByIdForUpdate(404L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.cancel(404L, 7L, Role.PATIENT)).isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- complete
    @Test
    void completeTransitionsBookedToCompletedAndAudits() {
        Appointment a = storedAppointment(NINE);
        AppointmentResponse response = service.complete(100L, 5L);
        assertThat(a.getStatus()).isEqualTo(AppointmentStatus.COMPLETED);
        assertThat(a.getSlotKey()).isEqualTo("5_203001210900");
        assertThat(response.status()).isEqualTo(AppointmentStatus.COMPLETED);
        verify(auditService).record(same(doctor), eq(AuditAction.COMPLETE), eq(AuditEntityType.APPOINTMENT), eq(100L), anyString());
    }

    @Test
    void onlyAssignedDoctorCanComplete() {
        storedAppointment(NINE);
        assertThatThrownBy(() -> service.complete(100L, 6L)).isInstanceOf(UnauthorizedAccessException.class);
        assertThatThrownBy(() -> service.complete(100L, 7L)).isInstanceOf(UnauthorizedAccessException.class);
        verify(auditService, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void completingNonBookedOrUnknownFails() {
        Appointment a = storedAppointment(NINE);
        a.cancel();
        assertThatThrownBy(() -> service.complete(100L, 5L)).isInstanceOf(InvalidBookingException.class);

        Appointment comp = storedAppointment(NINE);
        comp.complete();
        assertThatThrownBy(() -> service.complete(100L, 5L)).isInstanceOf(InvalidBookingException.class);

        when(appointmentRepository.findByIdForUpdate(404L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.complete(404L, 5L)).isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- reschedule
    @Test
    void rescheduleMovesTheAppointmentAndRulesIgnoreItsOwnId() {
        Appointment a = storedAppointment(NINE);
        AppointmentResponse response = service.reschedule(100L, 7L, new RescheduleRequest(TEN));

        assertThat(a.getStartTime()).isEqualTo(TEN);
        assertThat(a.getEndTime()).isEqualTo(TEN.plusMinutes(30));
        assertThat(a.getSlotKey()).isEqualTo("5_203001211000");
        assertThat(response.startTime()).isEqualTo(TEN);
        assertThat(seen.get(0).ignoredAppointmentId()).isEqualTo(100L);
        assertThat(seen.get(0).start()).isEqualTo(TEN);
        verify(auditService).record(same(patient), eq(AuditAction.RESCHEDULE), eq(AuditEntityType.APPOINTMENT), eq(100L), anyString());

        InOrder order = inOrder(appointmentRepository, doctorRepository);
        order.verify(appointmentRepository).findByIdForUpdate(100L);
        order.verify(doctorRepository).findByIdForUpdate(5L);
    }

    @Test
    void rescheduleKeepsTheOldSlotWhenARuleFails() {
        Appointment a = storedAppointment(NINE);
        failure = ctx -> { throw new SlotAlreadyBookedException("taken"); };
        assertThatThrownBy(() -> service.reschedule(100L, 7L, new RescheduleRequest(TEN)))
                .isInstanceOf(SlotAlreadyBookedException.class);
        assertThat(a.getStartTime()).isEqualTo(NINE);
        assertThat(a.getSlotKey()).isEqualTo("5_203001210900");
    }

    @Test
    void rescheduleChecksOwnershipAndStatus() {
        Appointment a = storedAppointment(NINE);
        assertThatThrownBy(() -> service.reschedule(100L, 8L, new RescheduleRequest(TEN)))
                .isInstanceOf(UnauthorizedAccessException.class);
        a.cancel();
        assertThatThrownBy(() -> service.reschedule(100L, 7L, new RescheduleRequest(TEN)))
                .isInstanceOf(AppointmentConflictException.class);
    }

    // ---- lists
    @Test
    void patientListCanFilterByStatus() {
        Appointment booked = new Appointment(patient, doctor, NINE, null);
        Appointment cancelled = new Appointment(patient, doctor, TEN, null);
        cancelled.cancel();
        when(appointmentRepository.findByPatientIdOrderByStartTimeDesc(7L)).thenReturn(List.of(cancelled, booked));

        assertThat(service.listForPatient(7L, null)).hasSize(2);
        assertThat(service.listForPatient(7L, AppointmentStatus.BOOKED)).extracting(AppointmentResponse::startTime)
                .containsExactly(NINE);
    }

    @Test
    void doctorListCanFilterByDateAndStatus() {
        Appointment monday = new Appointment(patient, doctor, NINE, null);
        Appointment tuesday = new Appointment(patient, doctor, NINE.plusDays(1), null);
        tuesday.complete();
        when(appointmentRepository.findByDoctorIdOrderByStartTimeDesc(5L)).thenReturn(List.of(tuesday, monday));

        assertThat(service.listForDoctor(5L, null, null)).hasSize(2);
        assertThat(service.listForDoctor(5L, NEXT_MONDAY, null)).extracting(AppointmentResponse::startTime).containsExactly(NINE);
        assertThat(service.listForDoctor(5L, null, AppointmentStatus.COMPLETED)).hasSize(1);
        assertThat(service.listForDoctor(5L, NEXT_MONDAY, AppointmentStatus.COMPLETED)).isEmpty();
    }

    // ---- admin oversight
    @Test
    void adminCancelCancelsBookedAppointmentAndAudits() {
        Appointment a = storedAppointment(NINE);
        AppointmentResponse response = service.adminCancel(100L, 1L);
        assertThat(a.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(a.getSlotKey()).isNull();
        assertThat(response.status()).isEqualTo(AppointmentStatus.CANCELLED);
        verify(auditService).record(same(admin), eq(AuditAction.CANCEL), eq(AuditEntityType.APPOINTMENT), eq(100L), anyString());
    }

    @Test
    void adminCancelRejectsNonBookedAppointment() {
        Appointment a = storedAppointment(NINE);
        a.cancel();
        assertThatThrownBy(() -> service.adminCancel(100L, 1L)).isInstanceOf(InvalidBookingException.class);

        Appointment comp = storedAppointment(NINE);
        comp.complete();
        assertThatThrownBy(() -> service.adminCancel(100L, 1L)).isInstanceOf(InvalidBookingException.class);
    }

    @Test
    void listForAdminAppliesFilters() {
        Appointment a = new Appointment(patient, doctor, NINE, "test");
        when(appointmentRepository.findFilteredForAdmin(eq(5L), eq(7L), eq(AppointmentStatus.BOOKED),
                any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(a)));

        List<AppointmentResponse> list = service.listForAdmin(5L, 7L, AppointmentStatus.BOOKED, NEXT_MONDAY, 0, 10);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).patientName()).isEqualTo("Pat Patient");
        assertThat(list.get(0).doctorName()).isEqualTo("Dr. Dan Doctor");
    }
}
