/**
 * api.js: Modular API client with automated CSRF token handling,
 * unified credentials ('same-origin'), and ApiError parsing.
 */

function getCookie(name) {
  const value = `; ${document.cookie}`;
  const parts = value.split(`; ${name}=`);
  if (parts.length === 2) {
    return decodeURIComponent(parts.pop().split(';').shift());
  }
  return null;
}

async function apiFetch(url, options = {}) {
  const method = (options.method || 'GET').toUpperCase();
  const headers = { ...options.headers };

  // CSRF protection: on state-changing calls, read cookie and attach X-XSRF-TOKEN
  if (['POST', 'PUT', 'PATCH', 'DELETE'].includes(method)) {
    const xsrfToken = getCookie('XSRF-TOKEN');
    if (xsrfToken) {
      headers['X-XSRF-TOKEN'] = xsrfToken;
    }
  }

  // Auto-set Content-Type if body is an object or array
  let body = options.body;
  if (body && typeof body === 'object' && !(body instanceof FormData) && !(body instanceof Blob)) {
    body = JSON.stringify(body);
    headers['Content-Type'] = 'application/json';
  }

  const fetchOptions = {
    ...options,
    method,
    headers,
    body,
    credentials: 'same-origin'
  };

  const response = await fetch(url, fetchOptions);

  // Handle session expiration / unauthorized access
  if (response.status === 401) {
    const currentPath = window.location.pathname;
    if (!currentPath.endsWith('login.html') && !currentPath.endsWith('register.html') && !currentPath.endsWith('index.html') && currentPath !== '/') {
      window.location.href = '/login.html?expired=true';
      return;
    }
  }

  if (!response.ok) {
    let errorDetail = {
      status: response.status,
      message: `Request failed with status ${response.status}`,
      fieldErrors: []
    };

    try {
      const errorJson = await response.json();
      if (errorJson) {
        errorDetail.message = errorJson.message || errorDetail.message;
        errorDetail.fieldErrors = errorJson.fieldErrors || [];
        if (errorDetail.fieldErrors.length > 0) {
          const fieldMsg = errorDetail.fieldErrors.map(f => `${f.field}: ${f.message}`).join(', ');
          errorDetail.message = `${errorDetail.message} (${fieldMsg})`;
        }
      }
    } catch (e) {
      // not a json response
      const text = await response.text().catch(() => '');
      if (text) errorDetail.message = text;
    }

    throw errorDetail;
  }

  // 204 No Content
  if (response.status === 204) {
    return null;
  }

  const contentType = response.headers.get('content-type') || '';
  if (contentType.includes('application/json')) {
    return await response.json();
  } else if (contentType.includes('text/')) {
    return await response.text();
  }

  return response;
}

// Alert helper
function showAlert(containerId, message, type = 'danger') {
  const container = document.getElementById(containerId);
  if (!container) return;
  container.innerHTML = `
    <div class="alert alert-${type}">
      <span>${escapeHtml(message)}</span>
      <button type="button" class="modal-close" style="font-size:1.1rem; line-height:1;" onclick="clearAlert('${containerId}')">&times;</button>
    </div>
  `;
}

function clearAlert(containerId) {
  const container = document.getElementById(containerId);
  if (container) container.innerHTML = '';
}

// Escaping
function escapeHtml(str) {
  if (!str) return '';
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

// Formatting helpers
function formatDateTime(isoString) {
  if (!isoString) return '-';
  const d = new Date(isoString);
  if (isNaN(d.getTime())) return isoString;
  return d.toLocaleString(undefined, {
    year: 'numeric', month: 'short', day: 'numeric',
    hour: '2-digit', minute: '2-digit'
  });
}

function formatDate(isoString) {
  if (!isoString) return '-';
  const d = new Date(isoString);
  if (isNaN(d.getTime())) return isoString;
  return d.toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' });
}

function formatTime(isoString) {
  if (!isoString) return '-';
  const d = new Date(isoString);
  if (isNaN(d.getTime())) return isoString;
  return d.toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' });
}

function getBadgeHtml(status) {
  if (!status) return '';
  const s = String(status).toUpperCase();
  const lower = s.toLowerCase();
  return `<span class="badge badge-${lower}">${escapeHtml(s)}</span>`;
}
