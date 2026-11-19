/**
 * auth.js: User authentication, session management, and role-based guards.
 */

async function getCurrentUser() {
  try {
    return await apiFetch('/api/auth/me');
  } catch (e) {
    return null;
  }
}

async function requireAuth(allowedRoles = []) {
  const user = await getCurrentUser();
  if (!user) {
    window.location.href = '/login.html';
    return null;
  }

  if (allowedRoles.length > 0 && !allowedRoles.includes(user.role)) {
    // Redirect to user's assigned dashboard
    if (user.role === 'PATIENT') window.location.href = '/patient.html';
    else if (user.role === 'DOCTOR') window.location.href = '/doctor.html';
    else if (user.role === 'ADMIN') window.location.href = '/admin.html';
    else window.location.href = '/index.html';
    return null;
  }

  initNavbarUser(user);
  return user;
}

function initNavbarUser(user) {
  const container = document.getElementById('navbarUser');
  if (!container) return;

  const roleBadgeClass = user.role.toLowerCase();
  container.innerHTML = `
    <div class="user-badge-bar">
      <span class="user-name">${escapeHtml(user.displayName || user.fullName)}</span>
      <span class="badge badge-${roleBadgeClass}">${escapeHtml(user.role)}</span>
      <button class="btn btn-secondary btn-sm" onclick="logout()">Sign Out</button>
    </div>
  `;
}

async function logout() {
  try {
    await apiFetch('/api/auth/logout', { method: 'POST' });
  } catch (e) {
    console.error('Logout error', e);
  } finally {
    window.location.href = '/login.html?loggedOut=true';
  }
}
