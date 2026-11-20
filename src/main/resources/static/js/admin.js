let currentAdmin = null;
let usersPage = 0;
let apptsPage = 0;
let auditPage = 0;
let loadedUsersMap = new Map();
const PAGE_SIZE = 15;

document.addEventListener('DOMContentLoaded', async () => {
  currentAdmin = await requireAuth(['ADMIN']);
  if (!currentAdmin) return;

  const today = new Date();
  const weekAgo = new Date(today);
  weekAgo.setDate(today.getDate() - 7);
  document.getElementById('reportFrom').value = weekAgo.toISOString().slice(0, 10);
  document.getElementById('reportTo').value = today.toISOString().slice(0, 10);

  document.getElementById('exportForm').addEventListener('submit', handleExportReport);
  document.getElementById('createDoctorForm').addEventListener('submit', handleCreateDoctor);
  document.getElementById('editUserForm').addEventListener('submit', handleEditUserSubmit);

  await Promise.all([loadStats(), loadUsers(), loadAdminAppointments(), loadAuditLogs()]);
});

function switchTab(tabId, btn) {
  document.querySelectorAll('.tab-pane').forEach(p => p.classList.remove('active'));
  document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
  document.getElementById(tabId).classList.add('active');
  if (btn) btn.classList.add('active');
}

async function loadStats() {
  try {
    const s = await apiFetch('/api/admin/stats');
    const items = [
      { label: 'Today Appointments', value: s.todayAppointments },
      { label: 'Booked Appointments', value: s.bookedAppointments },
      { label: 'Completed Appointments', value: s.completedAppointments },
      { label: 'Cancelled Appointments', value: s.cancelledAppointments },
      { label: 'Active Doctors', value: s.activeDoctors },
      { label: 'Inactive Doctors', value: s.inactiveDoctors },
      { label: 'Active Patients', value: s.activePatients },
      { label: 'Inactive Patients', value: s.inactivePatients },
      { label: 'Medical Records', value: s.totalMedicalRecords },
      { label: 'Prescriptions Issued', value: s.totalPrescriptions }
    ];
    document.getElementById('statsGrid').innerHTML = items.map(item => `
      <div style="border:1px solid var(--border, #e2e8f0); border-radius:8px; padding:1rem; background:var(--surface, #fff);">
        <div style="font-size:0.85rem; color:var(--text-muted, #64748b);">${escapeHtml(item.label)}</div>
        <div style="font-size:1.6rem; font-weight:700; margin-top:0.25rem;">${item.value ?? 0}</div>
      </div>
    `).join('');
  } catch (err) {
    showAlert('pageAlert', err.message || 'Failed to load clinic statistics');
  }
}

async function handleExportReport(e) {
  e.preventDefault();
  clearAlert('pageAlert');
  const from = document.getElementById('reportFrom').value;
  const to = document.getElementById('reportTo').value;
  const format = document.getElementById('reportFormat').value;

  try {
    const res = await fetch(`/api/admin/reports/appointments?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}&format=${encodeURIComponent(format)}`, {
      credentials: 'same-origin'
    });
    if (!res.ok) {
      let msg = `Export failed (${res.status})`;
      try {
        const err = await res.json();
        if (err && err.message) msg = err.message;
      } catch (_) {}
      throw new Error(msg);
    }
    const blob = await res.blob();
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `appointments-report.${format}`;
    document.body.appendChild(a);
    a.click();
    a.remove();
    URL.revokeObjectURL(url);
    showAlert('pageAlert', 'Report exported successfully.', 'success');
    loadAuditLogs();
  } catch (err) {
    showAlert('pageAlert', err.message || 'Failed to export report');
  }
}

async function handleCreateDoctor(e) {
  e.preventDefault();
  clearAlert('pageAlert');
  const payload = {
    fullName: document.getElementById('docFullName').value.trim(),
    email: document.getElementById('docEmail').value.trim(),
    password: document.getElementById('docPassword').value,
    specialization: document.getElementById('docSpecialization').value.trim(),
    licenseNumber: document.getElementById('docLicense').value.trim(),
    qualification: document.getElementById('docQualification').value.trim() || null,
    phone: document.getElementById('docPhone').value.trim() || null,
    slotMinutes: parseInt(document.getElementById('docSlotMinutes').value, 10) || 30
  };

  try {
    const created = await apiFetch('/api/admin/doctors', { method: 'POST', body: payload });
    e.target.reset();
    document.getElementById('docSlotMinutes').value = '30';
    showAlert('pageAlert', `Created doctor account for ${created.displayName || created.fullName}.`, 'success');
    await Promise.all([loadStats(), loadUsers(), loadAuditLogs()]);
  } catch (err) {
    showAlert('pageAlert', err.message || 'Failed to create doctor');
  }
}

function resetAndLoadUsers() {
  usersPage = 0;
  loadUsers();
}

function changeUsersPage(delta) {
  if (usersPage + delta < 0) return;
  usersPage += delta;
  loadUsers();
}

async function loadUsers() {
  const role = document.getElementById('userRoleFilter').value;
  const active = document.getElementById('userActiveFilter').value;
  const params = new URLSearchParams({ page: usersPage, size: PAGE_SIZE });
  if (role) params.set('role', role);
  if (active) params.set('active', active);

  try {
    const users = await apiFetch(`/api/admin/users?${params.toString()}`);
    loadedUsersMap.clear();
    const tbody = document.getElementById('usersTableBody');
    if (!users || users.length === 0) {
      tbody.innerHTML = '<tr><td colspan="7" style="text-align:center;">No users found.</td></tr>';
    } else {
      tbody.innerHTML = users.map(u => {
        loadedUsersMap.set(u.id, u);
        const isSelf = currentAdmin && u.id === currentAdmin.id;
        const statusBadge = u.active
          ? '<span class="badge badge-completed">ACTIVE</span>'
          : '<span class="badge badge-cancelled">INACTIVE</span>';
        const editBtn = `<button class="btn btn-secondary btn-sm" onclick="openEditUserModal(${u.id})">Edit</button>`;
        const toggleBtn = isSelf
          ? '<span style="font-size:0.8rem; color:#64748b;">Current Admin</span>'
          : `<button class="btn ${u.active ? 'btn-danger' : 'btn-primary'} btn-sm" onclick="toggleUserStatus(${u.id}, ${!u.active})">${u.active ? 'Deactivate' : 'Activate'}</button>`;
        return `
          <tr>
            <td>${u.id}</td>
            <td>${escapeHtml(u.displayName || u.fullName)}</td>
            <td>${escapeHtml(u.email)}</td>
            <td>${escapeHtml(u.phone || '-')}</td>
            <td>${getBadgeHtml(u.role)}</td>
            <td>${statusBadge}</td>
            <td style="display:flex; gap:0.4rem; align-items:center;">${editBtn} ${toggleBtn}</td>
          </tr>
        `;
      }).join('');
    }
    document.getElementById('usersPageLabel').textContent = `Page ${usersPage + 1}`;
    document.getElementById('usersPrevBtn').disabled = usersPage === 0;
    document.getElementById('usersNextBtn').disabled = !users || users.length < PAGE_SIZE;
  } catch (err) {
    showAlert('pageAlert', err.message || 'Failed to load users');
  }
}

async function openEditUserModal(userId) {
  const u = loadedUsersMap.get(userId);
  if (!u) return;
  document.getElementById('editUserId').value = u.id;
  document.getElementById('editUserRole').value = u.role;
  document.getElementById('editUserModalTitle').textContent = `Edit ${u.role} #${u.id}`;
  document.getElementById('editUserFullName').value = u.fullName || '';
  document.getElementById('editUserEmail').value = u.email || '';
  document.getElementById('editUserPhone').value = u.phone || '';

  const docBox = document.getElementById('editDoctorFields');
  if (u.role === 'DOCTOR') {
    docBox.style.display = 'block';
    document.getElementById('editDocSpecialization').value = '';
    document.getElementById('editDocQualification').value = '';
    document.getElementById('editDocSlotMinutes').value = '';
    try {
      const doc = await apiFetch(`/api/doctors/${u.id}`);
      document.getElementById('editDocSpecialization').value = doc.specialization || '';
      document.getElementById('editDocQualification').value = doc.qualification || '';
      document.getElementById('editDocSlotMinutes').value = doc.slotMinutes || 30;
    } catch (_) {}
  } else {
    docBox.style.display = 'none';
  }

  document.getElementById('editUserModal').style.display = 'flex';
}

function closeEditUserModal() {
  document.getElementById('editUserModal').style.display = 'none';
}

async function handleEditUserSubmit(e) {
  e.preventDefault();
  clearAlert('pageAlert');
  const userId = document.getElementById('editUserId').value;
  const role = document.getElementById('editUserRole').value;
  const payload = {
    fullName: document.getElementById('editUserFullName').value.trim(),
    email: document.getElementById('editUserEmail').value.trim(),
    phone: document.getElementById('editUserPhone').value.trim() || null
  };

  if (role === 'DOCTOR') {
    const spec = document.getElementById('editDocSpecialization').value.trim();
    const qual = document.getElementById('editDocQualification').value.trim();
    const slotVal = document.getElementById('editDocSlotMinutes').value;
    if (spec) payload.specialization = spec;
    payload.qualification = qual;
    if (slotVal) payload.slotMinutes = parseInt(slotVal, 10);
  }

  try {
    await apiFetch(`/api/admin/users/${userId}`, { method: 'PUT', body: payload });
    closeEditUserModal();
    showAlert('pageAlert', `Updated user #${userId}.`, 'success');
    await Promise.all([loadUsers(), loadAuditLogs()]);
  } catch (err) {
    showAlert('pageAlert', err.message || 'Failed to update user');
  }
}

async function toggleUserStatus(userId, active) {
  clearAlert('pageAlert');
  try {
    await apiFetch(`/api/admin/users/${userId}/status`, {
      method: 'PATCH',
      body: { active }
    });
    showAlert('pageAlert', `User #${userId} ${active ? 'activated' : 'deactivated'}.`, 'success');
    await Promise.all([loadStats(), loadUsers(), loadAuditLogs()]);
  } catch (err) {
    showAlert('pageAlert', err.message || 'Failed to update user status');
  }
}

function resetAndLoadAppts() {
  apptsPage = 0;
  loadAdminAppointments();
}

function changeApptsPage(delta) {
  if (apptsPage + delta < 0) return;
  apptsPage += delta;
  loadAdminAppointments();
}

async function loadAdminAppointments() {
  const date = document.getElementById('adminApptDate').value;
  const status = document.getElementById('adminApptStatus').value;
  const params = new URLSearchParams({ page: apptsPage, size: PAGE_SIZE });
  if (date) params.set('date', date);
  if (status) params.set('status', status);

  try {
    const appts = await apiFetch(`/api/admin/appointments?${params.toString()}`);
    const tbody = document.getElementById('adminApptsTableBody');
    if (!appts || appts.length === 0) {
      tbody.innerHTML = '<tr><td colspan="7" style="text-align:center;">No appointments found.</td></tr>';
    } else {
      tbody.innerHTML = appts.map(a => `
        <tr>
          <td>${a.id}</td>
          <td>${formatDateTime(a.startTime)}</td>
          <td>${escapeHtml(a.doctorName)} <small>(${escapeHtml(a.specialization)})</small></td>
          <td>${escapeHtml(a.patientName)}</td>
          <td>${escapeHtml(a.reason || '-')}</td>
          <td>${getBadgeHtml(a.status)}</td>
          <td>
            ${a.status === 'BOOKED'
              ? `<button class="btn btn-danger btn-sm" onclick="adminCancelAppt(${a.id})">Cancel</button>`
              : '-'}
          </td>
        </tr>
      `).join('');
    }
    document.getElementById('apptsPageLabel').textContent = `Page ${apptsPage + 1}`;
    document.getElementById('apptsPrevBtn').disabled = apptsPage === 0;
    document.getElementById('apptsNextBtn').disabled = !appts || appts.length < PAGE_SIZE;
  } catch (err) {
    showAlert('pageAlert', err.message || 'Failed to load appointments');
  }
}

async function adminCancelAppt(id) {
  clearAlert('pageAlert');
  try {
    await apiFetch(`/api/admin/appointments/${id}/cancel`, { method: 'POST' });
    showAlert('pageAlert', `Appointment #${id} cancelled.`, 'success');
    await Promise.all([loadStats(), loadAdminAppointments(), loadAuditLogs()]);
  } catch (err) {
    showAlert('pageAlert', err.message || 'Failed to cancel appointment');
  }
}

function changeAuditPage(delta) {
  if (auditPage + delta < 0) return;
  auditPage += delta;
  loadAuditLogs();
}

async function loadAuditLogs() {
  const params = new URLSearchParams({ page: auditPage, size: PAGE_SIZE });
  try {
    const logs = await apiFetch(`/api/admin/audit-logs?${params.toString()}`);
    const tbody = document.getElementById('auditTableBody');
    if (!logs || logs.length === 0) {
      tbody.innerHTML = '<tr><td colspan="5" style="text-align:center;">No audit logs found.</td></tr>';
    } else {
      tbody.innerHTML = logs.map(l => `
        <tr>
          <td>${formatDateTime(l.createdAt)}</td>
          <td>${escapeHtml(l.actorName || l.actorEmail)}</td>
          <td><strong>${escapeHtml(l.action)}</strong></td>
          <td>${escapeHtml(l.entityType)} #${l.entityId ?? '-'}</td>
          <td>${escapeHtml(l.details || '-')}</td>
        </tr>
      `).join('');
    }
    document.getElementById('auditPageLabel').textContent = `Page ${auditPage + 1}`;
    document.getElementById('auditPrevBtn').disabled = auditPage === 0;
    document.getElementById('auditNextBtn').disabled = !logs || logs.length < PAGE_SIZE;
  } catch (err) {
    showAlert('pageAlert', err.message || 'Failed to load audit logs');
  }
}