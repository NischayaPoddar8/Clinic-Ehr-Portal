/**
 * doctor.js: Doctor Dashboard â€” Appointments, Patient History,
 * Availability Management, Profile, and EHR (Medical Records).
 */

let currentUser = null;
let ehrAppointmentId = null;
let ehrRecordId = null;  // null = create mode, number = edit mode

// -- Tab switching --
function switchTab(tabId, btn) {
  document.querySelectorAll('.tab-pane').forEach(p => p.classList.remove('active'));
  document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
  document.getElementById(tabId).classList.add('active');
  btn.classList.add('active');

  if (tabId === 'scheduleTab') loadDoctorAppointments();
  if (tabId === 'availabilityTab') loadAvailability();
}

// -- Appointments --
async function loadDoctorAppointments() {
  const date = document.getElementById('apptDate').value;
  const status = document.getElementById('apptStatusFilter').value;
  const params = new URLSearchParams();
  if (date) params.set('date', date);
  if (status) params.set('status', status);

  const tbody = document.getElementById('apptTableBody');
  tbody.innerHTML = '<tr><td colspan="5" style="text-align:center;color:var(--slate-500);">Loading...</td></tr>';

  try {
    const appts = await apiFetch(`/api/doctor/appointments?${params.toString()}`);
    if (!appts || appts.length === 0) {
      tbody.innerHTML = '<tr><td colspan="5" class="empty-state">No appointments found.</td></tr>';
      return;
    }
    tbody.innerHTML = appts.map(a => {
      const isBooked = a.status === 'BOOKED';
      const isCompleted = a.status === 'COMPLETED';
      return `
        <tr>
          <td>${formatDateTime(a.startTime)}</td>
          <td>
            <div style="font-weight:600;color:var(--slate-800);">${escapeHtml(a.patientName || '-')}</div>
            <div style="font-size:0.78rem;color:var(--slate-500);">ID: #${a.patientId}</div>
          </td>
          <td>${escapeHtml(a.reason || '-')}</td>
          <td>${getBadgeHtml(a.status)}</td>
          <td style="white-space:nowrap;">
            ${isBooked ? `
              <button class="btn btn-primary btn-sm" onclick="completeAppointment(${a.id})" style="margin-right:0.25rem;">Complete</button>
              <button class="btn btn-secondary btn-sm" onclick="cancelAppointment(${a.id})" style="margin-right:0.25rem;">Cancel</button>
            ` : ''}
            ${isCompleted ? `
              <button class="btn btn-secondary btn-sm" onclick="openEhrModal(${a.id}, ${a.medicalRecordId ?? 'null'}, ${a.patientId})" style="margin-right:0.25rem;">
                ${a.medicalRecordId ? 'Edit EHR' : 'Create EHR'}
              </button>
            ` : ''}
            <button class="btn btn-outline btn-sm" onclick="viewPatientHistory(${a.patientId})">History</button>
          </td>
        </tr>
      `;
    }).join('');
  } catch (err) {
    tbody.innerHTML = '<tr><td colspan="5" class="empty-state">Failed to load appointments.</td></tr>';
    showAlert('pageAlert', err.message || 'Error loading appointments.');
  }
}

async function completeAppointment(id) {
  if (!confirm('Mark this appointment as COMPLETED?')) return;
  try {
    await apiFetch(`/api/doctor/appointments/${id}/complete`, { method: 'POST' });
    showAlert('pageAlert', 'Appointment marked as completed.', 'success');
    loadDoctorAppointments();
  } catch (err) {
    showAlert('pageAlert', err.message || 'Failed to complete appointment.');
  }
}

async function cancelAppointment(id) {
  if (!confirm('Cancel this appointment?')) return;
  try {
    await apiFetch(`/api/doctor/appointments/${id}/cancel`, { method: 'POST' });
    showAlert('pageAlert', 'Appointment cancelled.', 'success');
    loadDoctorAppointments();
  } catch (err) {
    showAlert('pageAlert', err.message || 'Failed to cancel appointment.');
  }
}

function viewPatientHistory(patientId) {
  const historyBtn = Array.from(document.querySelectorAll('.tab-btn'))
    .find(b => b.getAttribute('onclick')?.includes('historyTab'));
  if (historyBtn) switchTab('historyTab', historyBtn);
  document.getElementById('patientIdInput').value = patientId;
  searchPatientHistory();
}

// -- Patient History --
async function searchPatientHistory() {
  const patientId = document.getElementById('patientIdInput').value.trim();
  if (!patientId) {
    showAlert('pageAlert', 'Please enter a patient ID.');
    return;
  }

  const listEl = document.getElementById('patientHistoryList');
  listEl.innerHTML = '<p style="color:var(--slate-500);">Searching...</p>';

  try {
    const records = await apiFetch(`/api/doctor/patients/${patientId}/medical-records`);
    if (!records || records.length === 0) {
      listEl.innerHTML = '<p class="empty-state">No medical records found for this patient.</p>';
      return;
    }
    listEl.innerHTML = records.map(r => `
      <div style="border:1px solid var(--slate-200); border-radius:0.75rem; padding:1.25rem; margin-bottom:1rem;">
        <div style="display:flex; justify-content:space-between; align-items:flex-start; flex-wrap:wrap; gap:0.5rem;">
          <div>
            <div style="font-weight:700; color:var(--slate-800);">${escapeHtml(r.diagnosis || 'No diagnosis')}</div>
            <div style="color:var(--slate-500); font-size:0.85rem; margin-top:0.25rem;">
              ${escapeHtml(r.patientName || '-')} &bull; ${formatDate(r.appointmentStartTime)}
            </div>
            <div style="margin-top:0.5rem; color:var(--slate-600); font-size:0.9rem;">
              ${escapeHtml(r.notes || '')}
            </div>
          </div>
          <button class="btn btn-secondary btn-sm" onclick="openEhrModal(${r.appointmentId}, ${r.id}, null)">Edit EHR</button>
        </div>
        ${r.prescriptions && r.prescriptions.length > 0 ? `
          <div style="margin-top:0.75rem; font-size:0.85rem; color:var(--primary);">
            ${r.prescriptions.length} prescription(s): ${r.prescriptions.map(p => escapeHtml(p.medication)).join(', ')}
          </div>
        ` : ''}
      </div>
    `).join('');
  } catch (err) {
    listEl.innerHTML = '<p class="empty-state">Failed to load records.</p>';
    showAlert('pageAlert', err.message || 'Error loading patient history.');
  }
}

// -- Availability --
let cachedAvailability = [];

function selectPresetDays(days) {
  const set = new Set(days);
  document.querySelectorAll('#availDayPills input[type="checkbox"]').forEach(cb => {
    cb.checked = set.has(cb.value);
  });
}

async function loadAvailability() {
  const tbody = document.getElementById('availTableBody');
  tbody.innerHTML = '<tr><td colspan="4" style="text-align:center;color:var(--slate-500);">Loading...</td></tr>';

  try {
    const avails = await apiFetch('/api/doctor/availability');
    cachedAvailability = avails || [];
    if (cachedAvailability.length === 0) {
      tbody.innerHTML = '<tr><td colspan="4" class="empty-state">No availability windows set.</td></tr>';
      return;
    }
    const dayOrder = ['MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY','SATURDAY','SUNDAY'];
    cachedAvailability.sort((a, b) => dayOrder.indexOf(a.dayOfWeek) - dayOrder.indexOf(b.dayOfWeek) || a.startTime.localeCompare(b.startTime));
    tbody.innerHTML = cachedAvailability.map(a => `
      <tr>
        <td>${a.dayOfWeek.charAt(0) + a.dayOfWeek.slice(1).toLowerCase()}</td>
        <td>${a.startTime}</td>
        <td>${a.endTime}</td>
        <td><button class="btn btn-secondary btn-sm" onclick="deleteAvailability(${a.id})">Remove</button></td>
      </tr>
    `).join('');
  } catch (err) {
    tbody.innerHTML = '<tr><td colspan="4" class="empty-state">Failed to load availability.</td></tr>';
  }
}

async function addAvailability() {
  const days = Array.from(document.querySelectorAll('#availDayPills input[type="checkbox"]:checked')).map(cb => cb.value);
  const startTime = document.getElementById('availStart').value;
  const endTime = document.getElementById('availEnd').value;

  if (days.length === 0) {
    showAlert('availAlert', 'Please select at least one working day.');
    return;
  }
  if (!startTime || !endTime) {
    showAlert('availAlert', 'Please set both start and end times.');
    return;
  }

  const btn = document.getElementById('addAvailBtn');
  btn.disabled = true;
  btn.textContent = 'Saving...';

  let added = 0;
  const errors = [];
  for (const dayOfWeek of days) {
    try {
      await apiFetch('/api/doctor/availability', { method: 'POST', body: { dayOfWeek, startTime, endTime } });
      added++;
    } catch (err) {
      const label = dayOfWeek.charAt(0) + dayOfWeek.slice(1).toLowerCase();
      errors.push(`${label}: ${err.message || 'failed'}`);
    }
  }

  btn.disabled = false;
  btn.textContent = 'Add Schedule';

  if (errors.length === 0) {
    showAlert('availAlert', `Added schedule for ${added} day(s).`, 'success');
  } else if (added > 0) {
    showAlert('availAlert', `Added ${added} day(s); skipped ${errors.join('; ')}`, 'danger');
  } else {
    showAlert('availAlert', errors.join('; '), 'danger');
  }
  loadAvailability();
}

async function deleteAvailability(id) {
  if (!confirm('Remove this availability window?')) return;
  try {
    await apiFetch(`/api/doctor/availability/${id}`, { method: 'DELETE' });
    loadAvailability();
  } catch (err) {
    showAlert('pageAlert', err.message || 'Failed to remove availability.');
  }
}

async function clearAllAvailability() {
  if (!cachedAvailability || cachedAvailability.length === 0) return;
  if (!confirm(`Remove all ${cachedAvailability.length} availability window(s)?`)) return;
  try {
    await Promise.all(cachedAvailability.map(a => apiFetch(`/api/doctor/availability/${a.id}`, { method: 'DELETE' })));
    showAlert('availAlert', 'All availability windows removed.', 'success');
    loadAvailability();
  } catch (err) {
    showAlert('availAlert', err.message || 'Failed to clear all availability.');
    loadAvailability();
  }
}


// -- EHR Modal --
async function openEhrModal(appointmentId, recordId, patientId) {
  ehrAppointmentId = appointmentId;
  ehrRecordId = recordId;

  document.getElementById('ehrDiagnosis').value = '';
  document.getElementById('ehrNotes').value = '';
  document.getElementById('prescriptionRows').innerHTML = '';
  clearAlert('ehrAlert');

  if (recordId) {
    // Edit mode: load existing record
    document.getElementById('ehrModalTitle').textContent = 'Edit Medical Record';
    try {
      const r = await apiFetch(`/api/doctor/medical-records/${recordId}`);
      document.getElementById('ehrDiagnosis').value = r.diagnosis || '';
      document.getElementById('ehrNotes').value = r.notes || '';
      if (r.prescriptions) {
        r.prescriptions.forEach(p => addPrescriptionRow(p));
      }
    } catch (err) {
      showAlert('ehrAlert', 'Failed to load record for editing.');
    }
  } else {
    document.getElementById('ehrModalTitle').textContent = 'Create Medical Record';
  }

  document.getElementById('ehrModal').classList.add('open');
}

function closeEhrModal() {
  document.getElementById('ehrModal').classList.remove('open');
}

function addPrescriptionRow(data = {}) {
  const container = document.getElementById('prescriptionRows');
  const idx = container.children.length;
  const div = document.createElement('div');
  div.style.cssText = 'border:1px solid var(--slate-200); border-radius:0.5rem; padding:0.75rem; margin-bottom:0.75rem; position:relative;';
  div.innerHTML = `
    <button type="button" onclick="this.parentElement.remove()" style="position:absolute;top:0.5rem;right:0.5rem;background:none;border:none;cursor:pointer;font-size:1.1rem;color:var(--slate-400);">&times;</button>
    <div class="form-row" style="margin-bottom:0.5rem;">
      <div class="form-group"><label>Medication</label><input type="text" class="form-control prx-med" value="${escapeHtml(data.medication || '')}" placeholder="Drug name"></div>
      <div class="form-group"><label>Dosage</label><input type="text" class="form-control prx-dose" value="${escapeHtml(data.dosage || '')}" placeholder="e.g. 500mg"></div>
      <div class="form-group"><label>Frequency</label><input type="text" class="form-control prx-freq" value="${escapeHtml(data.frequency || '')}" placeholder="e.g. Twice daily"></div>
    </div>
    <div class="form-row">
      <div class="form-group"><label>Duration (days)</label><input type="number" class="form-control prx-dur" value="${data.durationDays || ''}" placeholder="e.g. 7" min="1"></div>
      <div class="form-group" style="flex:2;"><label>Instructions</label><input type="text" class="form-control prx-inst" value="${escapeHtml(data.instructions || '')}" placeholder="Take with food, etc."></div>
    </div>
  `;
  container.appendChild(div);
}

function collectPrescriptions() {
  const rows = document.querySelectorAll('#prescriptionRows > div');
  return Array.from(rows).map(row => ({
    medication: row.querySelector('.prx-med').value.trim(),
    dosage: row.querySelector('.prx-dose').value.trim(),
    frequency: row.querySelector('.prx-freq').value.trim(),
    durationDays: parseInt(row.querySelector('.prx-dur').value, 10) || null,
    instructions: row.querySelector('.prx-inst').value.trim()
  })).filter(p => p.medication);
}

async function saveEhr() {
  const diagnosis = document.getElementById('ehrDiagnosis').value.trim();
  if (!diagnosis) {
    showAlert('ehrAlert', 'Diagnosis is required.');
    return;
  }

  const body = {
    diagnosis,
    notes: document.getElementById('ehrNotes').value.trim(),
    prescriptions: collectPrescriptions()
  };

  try {
    if (ehrRecordId) {
      await apiFetch(`/api/doctor/medical-records/${ehrRecordId}`, { method: 'PUT', body });
    } else {
      await apiFetch(`/api/doctor/appointments/${ehrAppointmentId}/medical-record`, { method: 'POST', body });
    }
    closeEhrModal();
    showAlert('pageAlert', 'Medical record saved successfully.', 'success');
    loadDoctorAppointments();
  } catch (err) {
    showAlert('ehrAlert', err.message || 'Failed to save medical record.');
  }
}

// -- Page Init --
(async function init() {
  currentUser = await requireAuth(['DOCTOR']);
  if (!currentUser) return;
  loadDoctorAppointments();
})();
