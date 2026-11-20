/**
 * patient.js: Patient Portal â€” Doctor Search, Appointment Booking,
 * Reschedule, and Medical Records.
 */

let selectedDoctorId = null;
let selectedSlotTime = null;
let rescheduleAppointmentId = null;
let rescheduleDoctorId = null;
let rescheduleSelectedSlot = null;

// -- Tab switching --
function switchTab(tabId, btn) {
  document.querySelectorAll('.tab-pane').forEach(p => p.classList.remove('active'));
  document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
  document.getElementById(tabId).classList.add('active');
  btn.classList.add('active');

  if (tabId === 'appointmentsTab') loadMyAppointments();
  if (tabId === 'recordsTab') loadMyMedicalRecords();
}

// -- Doctor Search & Slot Booking --
async function searchDoctors() {
  const spec = document.getElementById('docSpec').value.trim();
  const name = document.getElementById('docName').value.trim();
  const params = new URLSearchParams();
  if (spec) params.set('specialization', spec);
  if (name) params.set('name', name);

  const listEl = document.getElementById('bookingDoctorsList');
  listEl.innerHTML = '<p style="color:var(--slate-500);">Searching...</p>';

  try {
    const doctors = await apiFetch(`/api/doctors?${params.toString()}`);
    if (!doctors || doctors.length === 0) {
      listEl.innerHTML = '<p class="empty-state">No doctors found. Try different filters.</p>';
      return;
    }
    listEl.innerHTML = doctors.map(d => `
      <div class="doctor-card">
        <div class="doctor-info">
          <div class="doctor-name">${escapeHtml(d.displayName || d.fullName)}</div>
          <div class="doctor-spec">${escapeHtml(d.specialization || 'General')}</div>
          <div style="font-size:0.8rem; color:var(--slate-500); margin-top:0.25rem;">
            ${d.qualification ? escapeHtml(d.qualification) : ''}
            ${d.slotMinutes ? ` &bull; ${d.slotMinutes}-min slots` : ''}
          </div>
        </div>
        <button class="btn btn-primary btn-sm" onclick="selectDoctorForBooking(${d.id}, '${escapeHtml(d.displayName || d.fullName)}')">
          Book
        </button>
      </div>
    `).join('');
  } catch (err) {
    listEl.innerHTML = '';
    showAlert('pageAlert', err.message || 'Failed to search doctors.');
  }
}

function selectDoctorForBooking(id, name) {
  selectedDoctorId = id;
  selectedSlotTime = null;
  document.getElementById('selectedDoctorName').textContent = `Book with ${name} - Select Date & Slot`;
  document.getElementById('slotBookingPanel').style.display = '';
  document.getElementById('slotsContainer').innerHTML = '';
  document.getElementById('bookingFormSection').style.display = 'none';
  clearAlert('slotsAlert');

  // Set booking window: tomorrow through +14 days
  const toLocalIso = (d) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  const tomorrow = new Date();
  tomorrow.setDate(tomorrow.getDate() + 1);
  const maxDate = new Date();
  maxDate.setDate(maxDate.getDate() + 14);
  const slotDateInput = document.getElementById('slotDate');
  slotDateInput.min = toLocalIso(tomorrow);
  slotDateInput.max = toLocalIso(maxDate);
  slotDateInput.value = '';

  document.getElementById('slotBookingPanel').scrollIntoView({ behavior: 'smooth', block: 'start' });
}

async function loadDoctorSlots() {
  const date = document.getElementById('slotDate').value;
  if (!date || !selectedDoctorId) return;

  const container = document.getElementById('slotsContainer');
  container.innerHTML = '<p style="color:var(--slate-500); font-size:0.9rem;">Loading slots...</p>';
  clearAlert('slotsAlert');
  document.getElementById('bookingFormSection').style.display = 'none';
  selectedSlotTime = null;

  try {
    const slots = await apiFetch(`/api/doctors/${selectedDoctorId}/slots?date=${date}`);
    if (!slots || slots.length === 0) {
      container.innerHTML = '<p class="empty-state">No available slots for this date.</p>';
      return;
    }
    container.innerHTML = slots.map(s => `
      <button class="slot-btn" onclick="selectSlot('${s.startTime}', '${s.endTime}', this)">
        ${formatTime(s.startTime)}
      </button>
    `).join('');
  } catch (err) {
    container.innerHTML = '';
    showAlert('slotsAlert', err.message || 'Failed to load slots.');
  }
}

function selectSlot(startTime, endTime, btn) {
  selectedSlotTime = startTime;
  document.querySelectorAll('#slotsContainer .slot-btn').forEach(b => b.classList.remove('selected'));
  btn.classList.add('selected');
  document.getElementById('bookingSlotSummary').textContent =
    `Selected: ${formatDateTime(startTime)} -> ${formatTime(endTime)}`;
  document.getElementById('bookingFormSection').style.display = '';
}

async function confirmBooking() {
  if (!selectedDoctorId || !selectedSlotTime) {
    showAlert('slotsAlert', 'Please select a slot first.');
    return;
  }
  const reason = document.getElementById('bookingReason').value.trim();
  const btn = document.getElementById('confirmBookBtn');
  btn.disabled = true;
  btn.textContent = 'Booking...';

  try {
    await apiFetch('/api/patient/appointments', {
      method: 'POST',
      body: { doctorId: selectedDoctorId, startTime: selectedSlotTime, reason }
    });
    showAlert('pageAlert', 'Appointment booked successfully!', 'success');

    // Reset booking panel
    document.getElementById('slotBookingPanel').style.display = 'none';
    document.getElementById('slotsContainer').innerHTML = '';
    document.getElementById('bookingFormSection').style.display = 'none';
    document.getElementById('bookingReason').value = '';
    selectedDoctorId = null;
    selectedSlotTime = null;

    // Switch to appointments tab
    const apptBtn = document.querySelectorAll('.tab-btn')[1];
    switchTab('appointmentsTab', apptBtn);
  } catch (err) {
    showAlert('slotsAlert', err.message || 'Failed to book appointment.');
  } finally {
    btn.disabled = false;
    btn.textContent = 'Confirm & Book Appointment';
  }
}

// -- My Appointments --
async function loadMyAppointments() {
  const status = document.getElementById('apptStatusFilter').value;
  const params = new URLSearchParams();
  if (status) params.set('status', status);

  const tbody = document.getElementById('appointmentsTableBody');
  tbody.innerHTML = '<tr><td colspan="6" style="text-align:center; color:var(--slate-500);">Loading...</td></tr>';

  try {
    const appts = await apiFetch(`/api/patient/appointments?${params.toString()}`);
    if (!appts || appts.length === 0) {
      tbody.innerHTML = '<tr><td colspan="6" class="empty-state">No appointments found.</td></tr>';
      return;
    }
    tbody.innerHTML = appts.map(a => {
      const isBooked = a.status === 'BOOKED';
      return `
        <tr>
          <td>${formatDateTime(a.startTime)}</td>
          <td>${escapeHtml(a.doctorName || '-')}</td>
          <td>${escapeHtml(a.specialization || '-')}</td>
          <td>${getBadgeHtml(a.status)}</td>
          <td>${escapeHtml(a.reason || '-')}</td>
          <td>
            ${isBooked ? `
              <button class="btn btn-secondary btn-sm" onclick="cancelAppointment(${a.id})" style="margin-right:0.3rem;">Cancel</button>
              <button class="btn btn-primary btn-sm" onclick="openRescheduleModal(${a.id}, ${a.doctorId})">Reschedule</button>
            ` : '-'}
          </td>
        </tr>
      `;
    }).join('');
  } catch (err) {
    tbody.innerHTML = '<tr><td colspan="6" class="empty-state">Failed to load appointments.</td></tr>';
    showAlert('pageAlert', err.message || 'Error loading appointments.');
  }
}

async function cancelAppointment(id) {
  if (!confirm('Cancel this appointment?')) return;
  try {
    await apiFetch(`/api/patient/appointments/${id}/cancel`, { method: 'POST' });
    showAlert('pageAlert', 'Appointment cancelled.', 'success');
    loadMyAppointments();
  } catch (err) {
    showAlert('pageAlert', err.message || 'Failed to cancel appointment.');
  }
}

// -- Reschedule Modal --
function openRescheduleModal(appointmentId, doctorId) {
  rescheduleAppointmentId = appointmentId;
  rescheduleDoctorId = doctorId;
  rescheduleSelectedSlot = null;

  const toLocalIso = (d) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  const tomorrow = new Date();
  tomorrow.setDate(tomorrow.getDate() + 1);
  const maxDate = new Date();
  maxDate.setDate(maxDate.getDate() + 14);
  const dateInput = document.getElementById('rescheduleDate');
  dateInput.min = toLocalIso(tomorrow);
  dateInput.max = toLocalIso(maxDate);
  dateInput.value = '';

  document.getElementById('rescheduleSlotsContainer').innerHTML = '';
  document.getElementById('confirmRescheduleBtn').disabled = true;
  clearAlert('rescheduleAlert');
  document.getElementById('rescheduleModal').classList.add('open');
}

async function loadRescheduleSlots() {
  const date = document.getElementById('rescheduleDate').value;
  if (!date || !rescheduleDoctorId) return;

  const container = document.getElementById('rescheduleSlotsContainer');
  container.innerHTML = '<p style="color:var(--slate-500); font-size:0.9rem;">Loading...</p>';
  rescheduleSelectedSlot = null;
  document.getElementById('confirmRescheduleBtn').disabled = true;

  try {
    const slots = await apiFetch(`/api/doctors/${rescheduleDoctorId}/slots?date=${date}`);
    if (!slots || slots.length === 0) {
      container.innerHTML = '<p class="empty-state">No available slots for this date.</p>';
      return;
    }
    container.innerHTML = slots.map(s => `
      <button class="slot-btn" onclick="selectRescheduleSlot('${s.startTime}', this)">
        ${formatTime(s.startTime)}
      </button>
    `).join('');
  } catch (err) {
    container.innerHTML = '';
    showAlert('rescheduleAlert', err.message || 'Failed to load slots.');
  }
}

function selectRescheduleSlot(startTime, btn) {
  rescheduleSelectedSlot = startTime;
  document.querySelectorAll('#rescheduleSlotsContainer .slot-btn').forEach(b => b.classList.remove('selected'));
  btn.classList.add('selected');
  document.getElementById('confirmRescheduleBtn').disabled = false;
}

async function confirmReschedule() {
  if (!rescheduleAppointmentId || !rescheduleSelectedSlot) return;
  const btn = document.getElementById('confirmRescheduleBtn');
  btn.disabled = true;
  btn.textContent = 'Saving...';

  try {
    await apiFetch(`/api/patient/appointments/${rescheduleAppointmentId}/reschedule`, {
      method: 'PUT',
      body: { newStartTime: rescheduleSelectedSlot }
    });
    closeRescheduleModal();
    showAlert('pageAlert', 'Appointment rescheduled successfully!', 'success');
    loadMyAppointments();
  } catch (err) {
    showAlert('rescheduleAlert', err.message || 'Failed to reschedule.');
  } finally {
    btn.disabled = false;
    btn.textContent = 'Save New Time';
  }
}

function closeRescheduleModal() {
  document.getElementById('rescheduleModal').classList.remove('open');
}

// -- Medical Records --
async function loadMyMedicalRecords() {
  const listEl = document.getElementById('medicalRecordsList');
  listEl.innerHTML = '<p style="color:var(--slate-500);">Loading records...</p>';

  try {
    const records = await apiFetch('/api/patient/medical-records');
    if (!records || records.length === 0) {
      listEl.innerHTML = '<p class="empty-state">No medical records found.</p>';
      return;
    }
    listEl.innerHTML = records.map(r => `
      <div class="record-card" style="border:1px solid var(--slate-200); border-radius:0.75rem; padding:1.25rem; margin-bottom:1rem;">
        <div style="display:flex; justify-content:space-between; align-items:flex-start;">
          <div>
            <div style="font-weight:700; color:var(--slate-800); font-size:1rem;">${escapeHtml(r.diagnosis || 'No diagnosis')}</div>
            <div style="color:var(--slate-500); font-size:0.85rem; margin-top:0.25rem;">
              ${escapeHtml(r.doctorName || '-')} &bull; ${formatDate(r.appointmentStartTime)}
            </div>
            <div style="color:var(--slate-600); font-size:0.85rem; margin-top:0.25rem;">
              ${r.prescriptions && r.prescriptions.length > 0
                ? `${r.prescriptions.length} prescription(s)`
                : 'No prescriptions'}
            </div>
          </div>
          <button class="btn btn-secondary btn-sm" onclick="openRecordDetail(${r.id})">View Details</button>
        </div>
      </div>
    `).join('');
  } catch (err) {
    listEl.innerHTML = '<p class="empty-state">Failed to load records.</p>';
    showAlert('pageAlert', err.message || 'Error loading medical records.');
  }
}

async function openRecordDetail(id) {
  const contentEl = document.getElementById('recordDetailContent');
  contentEl.innerHTML = '<p style="color:var(--slate-500);">Loading...</p>';
  document.getElementById('recordDetailModal').classList.add('open');

  try {
    const r = await apiFetch(`/api/patient/medical-records/${id}`);
    document.getElementById('recordDetailTitle').textContent =
      `Consultation - ${formatDate(r.appointmentStartTime)}`;

    let prescriptionsHtml = '';
    if (r.prescriptions && r.prescriptions.length > 0) {
      prescriptionsHtml = `
        <h5 style="font-weight:700; margin:1.25rem 0 0.5rem;">Prescriptions</h5>
        <div class="table-responsive">
          <table>
            <thead><tr><th>Medication</th><th>Dosage</th><th>Frequency</th><th>Duration</th><th>Instructions</th></tr></thead>
            <tbody>
              ${r.prescriptions.map(p => `
                <tr>
                  <td>${escapeHtml(p.medication)}</td>
                  <td>${escapeHtml(p.dosage || '-')}</td>
                  <td>${escapeHtml(p.frequency || '-')}</td>
                  <td>${p.durationDays ? `${p.durationDays} day(s)` : '-'}</td>
                  <td>${escapeHtml(p.instructions || '-')}</td>
                </tr>
              `).join('')}
            </tbody>
          </table>
        </div>
      `;
    } else {
      prescriptionsHtml = '<p style="color:var(--slate-500); margin-top:0.75rem;">No prescriptions issued.</p>';
    }

    contentEl.innerHTML = `
      <div style="margin-bottom:0.75rem;">
        <span style="font-size:0.8rem; color:var(--slate-500);">Doctor</span>
        <div style="font-weight:600;">${escapeHtml(r.doctorName || '-')} &bull; ${escapeHtml(r.specialization || '')}</div>
      </div>
      <div style="margin-bottom:0.75rem;">
        <span style="font-size:0.8rem; color:var(--slate-500);">Appointment</span>
        <div>${formatDateTime(r.appointmentStartTime)} -> ${formatTime(r.appointmentEndTime)}</div>
      </div>
      <div style="margin-bottom:0.75rem;">
        <span style="font-size:0.8rem; color:var(--slate-500);">Diagnosis</span>
        <div style="font-weight:600; color:var(--slate-800);">${escapeHtml(r.diagnosis || '-')}</div>
      </div>
      ${r.notes ? `
        <div style="margin-bottom:0.75rem;">
          <span style="font-size:0.8rem; color:var(--slate-500);">Clinical Notes</span>
          <div style="background:var(--slate-50); border-radius:0.5rem; padding:0.75rem; white-space:pre-wrap;">${escapeHtml(r.notes)}</div>
        </div>
      ` : ''}
      ${prescriptionsHtml}
      <div style="margin-top:1rem; font-size:0.8rem; color:var(--slate-400);">
        Created: ${formatDateTime(r.createdAt)}
        ${r.updatedAt && r.updatedAt !== r.createdAt ? ` &bull; Updated: ${formatDateTime(r.updatedAt)}` : ''}
      </div>
    `;
  } catch (err) {
    contentEl.innerHTML = `<p class="empty-state">Failed to load record details.</p>`;
  }
}

function closeRecordDetailModal() {
  document.getElementById('recordDetailModal').classList.remove('open');
}

// -- Page Init --
(async function init() {
  const user = await requireAuth(['PATIENT']);
  if (!user) return;

  // Auto-load appointments and initial doctor list
  await Promise.all([loadMyAppointments(), searchDoctors()]);

  // Check if deep-linking to a specific doctor
  const params = new URLSearchParams(window.location.search);
  const bookDoctorId = params.get('bookDoctor');
  if (bookDoctorId) {
    try {
      const doc = await apiFetch(`/api/doctors/${bookDoctorId}`);
      selectDoctorForBooking(doc.id, doc.displayName || doc.fullName);
    } catch (e) { /* ignore */ }
  }
})();
