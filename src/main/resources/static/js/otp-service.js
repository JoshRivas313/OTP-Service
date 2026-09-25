const ICON_OK = '<svg width="16" height="16" viewBox="0 0 24 24" fill="none"><path d="M20 6L9 17l-5-5" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"/></svg>';
const ICON_ERR = '<svg width="16" height="16" viewBox="0 0 24 24" fill="none"><path d="M18 6L6 18M6 6l12 12" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"/></svg>';

function validateE164(cellphone) {
  return /^\d{7,9}$/.test(cellphone);
}

function showFieldError(fieldId, message) {
  const input = document.getElementById(fieldId);
  const existingError = input.parentElement.querySelector('.field-error');
  if (existingError) existingError.remove();
  input.classList.add('error');
  if (message) {
    const errorEl = document.createElement('span');
    errorEl.className = 'field-error';
    errorEl.textContent = message;
    input.parentElement.appendChild(errorEl);
  }
}

function clearFieldError(fieldId) {
  const input = document.getElementById(fieldId);
  const existingError = input.parentElement.querySelector('.field-error');
  if (existingError) existingError.remove();
  input.classList.remove('error');
}

document.querySelectorAll('input[inputmode="numeric"]').forEach(input => {
  input.addEventListener('input', () => {
    input.value = input.value.replace(/\D/g, '');
    clearFieldError(input.id);
  });

  input.addEventListener('blur', () => {
    if (input.value && !validateE164(input.value)) {
      showFieldError(input.id, 'Número debe tener 7-9 dígitos');
    }
  });
});

function setProgressStep(number, state) {
  const step = document.getElementById('progress-step-' + number);
  const dot = step.querySelector('.otp-progress-dot');
  step.classList.remove('active', 'done');
  dot.classList.remove('active', 'done');
  if (state) {
    step.classList.add(state);
    dot.classList.add(state);
  }
  const line = document.getElementById('progress-line-' + number);
  if (line) {
    line.classList.toggle('done', state === 'done');
  }
}

function resetProgress() {
  setProgressStep(1, null);
  setProgressStep(2, null);
  setProgressStep(3, null);
}

let codeInputListenerAttached = false;

function showOtpStep(stepNumber) {
  document.querySelectorAll('.otp-step').forEach(step => {
    step.style.display = 'none';
  });
  const step = document.getElementById('otp-step-' + stepNumber);
  if (step) step.style.display = 'block';
}

function resetOtpFlow() {
  showOtpStep(1);
  resetProgress();
  document.getElementById('gen-cellphone').value = '';
  document.getElementById('gen-result').className = 'result';
  document.getElementById('gen-result').innerHTML = '';
  document.getElementById('ver-cellphone').value = '';
  document.getElementById('ver-code').value = '';
  document.getElementById('ver-result').className = 'result';
  document.getElementById('ver-result').innerHTML = '';
}

async function generateOtp() {
  const cellphoneInput = document.getElementById('gen-cellphone');
  const cellphone = cellphoneInput.value.trim();

  if (!cellphone) {
    showFieldError('gen-cellphone', 'Número requerido');
    return;
  }

  if (!validateE164(cellphone)) {
    showFieldError('gen-cellphone', 'Número debe tener 7-9 dígitos');
    return;
  }

  clearFieldError('gen-cellphone');
  const digits = parseInt(document.getElementById('gen-digits').value, 10);
  const durationSeconds = parseInt(document.getElementById('gen-expiration').value, 10);
  const ok = await submit('gen-btn', 'gen-result', '/api/twilio/otps', { cellphone, digits, durationSeconds });
  if (ok) {
    resetProgress();
    setProgressStep(1, 'done');
    document.getElementById('ver-cellphone').value = cellphone;

    showOtpStep(2);
    const codeInput = document.getElementById('ver-code');
    codeInput.value = '';
    codeInput.focus();

    if (!codeInputListenerAttached) {
      codeInputListenerAttached = true;
      codeInput.addEventListener('input', () => {
        if (codeInput.value.length > 0) {
          setProgressStep(2, 'active');
        }
      });
    }
  }
}

async function verifyOtp() {
  const cellphoneInput = document.getElementById('ver-cellphone');
  const codeInput = document.getElementById('ver-code');
  const cellphone = cellphoneInput.value.trim();
  const code = codeInput.value.trim();

  if (!cellphone) {
    showFieldError('ver-cellphone', 'Número requerido');
    return;
  }

  if (!validateE164(cellphone)) {
    showFieldError('ver-cellphone', 'Número debe tener 7-9 dígitos');
    return;
  }

  if (!code) {
    showFieldError('ver-code', 'Código requerido');
    return;
  }

  if (code.length < 4) {
    showFieldError('ver-code', 'Código debe tener al menos 4 dígitos');
    return;
  }

  clearFieldError('ver-cellphone');
  clearFieldError('ver-code');
  const ok = await submit('ver-btn', 'ver-result', '/api/twilio/otps/verify', { cellphone, code });
  if (ok) {
    setProgressStep(2, 'done');
    setProgressStep(3, 'done');
    setTimeout(() => {
      showOtpStep(3);
    }, 300);
  }
}

async function submit(buttonId, resultId, url, body) {
  const btn = document.getElementById(buttonId);
  btn.disabled = true;
  btn.classList.add('loading');
  btn.setAttribute('aria-busy', 'true');
  let ok = false;
  try {
    const response = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body)
    });
    const data = await response.json();
    ok = response.ok;

    if (ok && resultId === 'ver-result') {
      showOtpSuccessOverlay();
    }
    showResult(resultId, ok, data.message || data.code);
  } catch (error) {
    showResult(resultId, false, 'No se pudo conectar con el servidor');
  } finally {
    btn.disabled = false;
    btn.classList.remove('loading');
    btn.setAttribute('aria-busy', 'false');
  }
  return ok;
}

function showResult(elementId, ok, message) {
  const el = document.getElementById(elementId);
  el.innerHTML = (ok ? ICON_OK : ICON_ERR) + '<span>' + message + '</span>';
  el.className = 'result show ' + (ok ? 'ok' : 'err');
}

function spawnOtpSuccessParticles(container) {
  if (!container || window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;
  const count = 12;
  for (let i = 0; i < count; i++) {
    const particle = document.createElement('span');
    particle.className = 'otp-success-particle';
    const angle = (Math.PI * 2 * i) / count + Math.random() * 0.4;
    const distance = 50 + Math.random() * 35;
    particle.style.setProperty('--dx', Math.cos(angle) * distance + 'px');
    particle.style.setProperty('--dy', Math.sin(angle) * distance + 'px');
    particle.style.animationDelay = Math.round(Math.random() * 100) + 'ms';
    container.appendChild(particle);
    setTimeout(() => particle.remove(), 1300);
  }
}

function showOtpSuccessOverlay() {
  const overlay = document.getElementById('otp-success-overlay');
  overlay.style.display = 'flex';
  spawnOtpSuccessParticles(document.querySelector('.otp-success-particles'));
  setTimeout(() => {
    overlay.style.display = 'none';
  }, 1800);
}

async function checkTwilioBanner() {
  try {
    const statusResponse = await fetch('/api/twilio/status');
    const status = await statusResponse.json();
    document.getElementById('twilio-status-bar').style.display = 'flex';
    document.getElementById('twilio-connected-badge').style.display = status.connected ? 'flex' : 'none';
    document.getElementById('twilio-disconnect-btn').style.display = status.connected ? 'inline' : 'none';
    document.getElementById('twilio-connect-link').style.display = status.connected ? 'none' : 'inline';
    if (status.connected) {
      document.getElementById('tw-masked-inline').textContent = status.maskedCredentials;
    }
  } catch (error) {
  }
}

async function changeTwilioConfig() {
  await fetch('/api/twilio/disconnect', { method: 'POST' });
  window.location.href = '/';
}

checkTwilioBanner();
