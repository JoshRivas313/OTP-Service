const ICON_OK = '<svg width="16" height="16" viewBox="0 0 24 24" fill="none"><path d="M20 6L9 17l-5-5" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"/></svg>';
const ICON_ERR = '<svg width="16" height="16" viewBox="0 0 24 24" fill="none"><path d="M18 6L6 18M6 6l12 12" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"/></svg>';

document.querySelectorAll('input[inputmode="numeric"]').forEach(input => {
  input.addEventListener('input', () => {
    input.value = input.value.replace(/\D/g, '');
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

async function generateOtp() {
  const cellphone = document.getElementById('gen-cellphone').value.trim();
  const digits = parseInt(document.getElementById('gen-digits').value, 10);
  const durationSeconds = parseInt(document.getElementById('gen-expiration').value, 10);
  const ok = await submit('gen-btn', 'gen-result', '/api/twilio/otps', { cellphone, digits, durationSeconds });
  if (ok) {
    resetProgress();
    setProgressStep(1, 'done');
    document.getElementById('ver-cellphone').value = cellphone;
    document.getElementById('verify-card').classList.add('active-step');
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
  const cellphone = document.getElementById('ver-cellphone').value.trim();
  const code = document.getElementById('ver-code').value.trim();
  const ok = await submit('ver-btn', 'ver-result', '/api/twilio/otps/verify', { cellphone, code });
  if (ok) {
    setProgressStep(2, 'done');
    setProgressStep(3, 'done');
  }
}

async function submit(buttonId, resultId, url, body) {
  const btn = document.getElementById(buttonId);
  btn.disabled = true;
  btn.classList.add('loading');
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
