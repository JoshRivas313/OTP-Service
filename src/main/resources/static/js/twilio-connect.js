const ICON_ERR = '<svg width="16" height="16" viewBox="0 0 24 24" fill="none"><path d="M18 6L6 18M6 6l12 12" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"/></svg>';
const ICON_OK = '<svg width="16" height="16" viewBox="0 0 24 24" fill="none"><path d="M20 6L9 17l-5-5" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"/></svg>';

document.querySelectorAll('input[inputmode="numeric"]').forEach(input => {
  input.addEventListener('input', () => {
    input.value = input.value.replace(/\D/g, '');
  });
});

function showError(elementId, message) {
  const el = document.getElementById(elementId);
  el.innerHTML = ICON_ERR + '<span>' + message + '</span>';
  el.className = 'result show err';
}

async function connectTwilio() {
  const btn = document.getElementById('tw-connect-btn');
  const label = btn.querySelector('.btn-label');
  btn.disabled = true;
  btn.classList.add('loading');
  label.textContent = 'Verificando credenciales...';
  try {
    const response = await fetch('/api/twilio/connect', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        accountSid: document.getElementById('tw-account-sid').value.trim(),
        authToken: document.getElementById('tw-auth-token').value.trim(),
        verifyServiceSid: document.getElementById('tw-verify-sid').value.trim(),
        phoneNumber: document.getElementById('tw-phone-number').value.trim()
      })
    });
    const data = await response.json();
    if (response.ok) {
      document.getElementById('tw-auth-token').value = '';
      // Feedback breve dentro del propio boton, despues navega directo a OTP Service
      btn.classList.remove('loading');
      btn.classList.add('success');
      label.innerHTML = ICON_OK + ' Conectado';
      setTimeout(() => {
        window.location.href = '/otp-service.html';
      }, 650);
      return;
    }
    showError('tw-connect-result',
        'No pudimos validar estas credenciales.<span class="result-detail">' + (data.message || data.code) + '</span>');
  } catch (error) {
    showError('tw-connect-result', 'No se pudo conectar con el servidor');
  } finally {
    if (!btn.classList.contains('success')) {
      btn.disabled = false;
      btn.classList.remove('loading');
      label.textContent = 'Conectar con Twilio';
    }
  }
}
