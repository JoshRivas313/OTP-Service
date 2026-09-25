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
let timerInterval = null;

function attachCodeBoxListeners() {
  const boxes = document.querySelectorAll('.code-box');
  boxes.forEach((box, index) => {
    box.addEventListener('input', (e) => {
      e.target.value = e.target.value.replace(/\D/g, '');
      if (e.target.value && index < boxes.length - 1) {
        boxes[index + 1].focus();
      }
      updateVerifyButtonState();
    });
    box.addEventListener('keydown', (e) => {
      if (e.key === 'Backspace' && !e.target.value && index > 0) {
        boxes[index - 1].focus();
      }
    });
    box.addEventListener('paste', (e) => {
      e.preventDefault();
      const paste = (e.clipboardData || window.clipboardData).getData('text');
      const digits = paste.replace(/\D/g, '').split('');
      digits.forEach((digit, i) => {
        if (index + i < boxes.length) {
          boxes[index + i].value = digit;
        }
      });
      if (digits.length > 0) {
        boxes[Math.min(index + digits.length - 1, boxes.length - 1)].focus();
      }
      updateVerifyButtonState();
    });
  });
}

function updateVerifyButtonState() {
  const code = Array.from(document.querySelectorAll('.code-box')).map(b => b.value).join('');
  const btn = document.getElementById('ver-btn');
  btn.disabled = code.length < 6;
}

function startTimer(durationSeconds) {
  clearInterval(timerInterval);
  let remaining = durationSeconds;
  const timerDisplay = document.getElementById('timer-display');
  const timerRing = document.getElementById('timer-ring');

  const updateTimer = () => {
    timerDisplay.textContent = remaining;
    const progress = ((durationSeconds - remaining) / durationSeconds) * 360;
    timerRing.style.setProperty('--timer-progress', progress + 'deg');

    if (remaining <= 0) {
      clearInterval(timerInterval);
      timerRing.classList.add('expired');
      document.querySelectorAll('.code-box').forEach(box => {
        box.disabled = true;
      });
      document.getElementById('ver-btn').disabled = true;
    }
    remaining--;
  };

  updateTimer();
  timerInterval = setInterval(updateTimer, 1000);
}

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
  clearInterval(timerInterval);
  document.getElementById('gen-cellphone').value = '';
  document.getElementById('gen-result').className = 'result';
  document.getElementById('gen-result').innerHTML = '';
  document.getElementById('gen-success').style.display = 'none';
  document.getElementById('ver-phone-display').textContent = '+51 ••••••••';
  document.getElementById('ver-result').className = 'result';
  document.getElementById('ver-result').innerHTML = '';
  document.querySelectorAll('.code-box').forEach(box => {
    box.value = '';
    box.classList.remove('error');
    box.disabled = false;
  });
  document.getElementById('timer-ring').classList.remove('expired');
  document.getElementById('timer-display').textContent = '--';
  document.getElementById('ver-btn').disabled = true;
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

    document.getElementById('gen-success').style.display = 'flex';
    const masked = cellphone.slice(0, 2) + '*'.repeat(cellphone.length - 4) + cellphone.slice(-2);
    document.getElementById('gen-phone-display').textContent = '+51 ' + masked;

    const displayPhone = '+51 ' + cellphone.slice(0, 2) + '****' + cellphone.slice(-2);
    document.getElementById('ver-phone-display').textContent = displayPhone;

    setTimeout(() => {
      showOtpStep(2);
      if (!codeInputListenerAttached) {
        codeInputListenerAttached = true;
        attachCodeBoxListeners();
        document.querySelectorAll('.code-box').forEach(box => {
          box.addEventListener('input', () => {
            if (Array.from(document.querySelectorAll('.code-box')).some(b => b.value)) {
              setProgressStep(2, 'active');
            }
          });
        });
      }
      startTimer(durationSeconds);
      document.querySelector('.code-box').focus();
    }, 600);
  }
}

async function verifyOtp() {
  const code = Array.from(document.querySelectorAll('.code-box')).map(b => b.value).join('').trim();

  if (!code || code.length < 4) {
    document.querySelectorAll('.code-box').forEach(box => box.classList.add('error'));
    setTimeout(() => {
      document.querySelectorAll('.code-box').forEach(box => box.classList.remove('error'));
    }, 300);
    return;
  }

  const cellphone = document.getElementById('gen-cellphone').value.trim();

  clearInterval(timerInterval);
  const ok = await submit('ver-btn', 'ver-result', '/api/twilio/otps/verify', { cellphone, code });
  if (ok) {
    document.querySelectorAll('.code-box').forEach(box => box.disabled = true);
    setProgressStep(2, 'done');
    setProgressStep(3, 'done');
    setTimeout(() => {
      showOtpStep(3);
    }, 300);
  } else {
    document.querySelectorAll('.code-box').forEach(box => box.classList.add('error'));
    setTimeout(() => {
      document.querySelectorAll('.code-box').forEach(box => box.classList.remove('error'));
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
  const count = 24;
  for (let i = 0; i < count; i++) {
    const particle = document.createElement('span');
    particle.className = 'otp-success-particle';
    const angle = (Math.PI * 2 * i) / count + (Math.random() - 0.5) * 0.6;
    const distance = 40 + Math.random() * 60;
    const duration = 800 + Math.random() * 400;
    particle.style.setProperty('--dx', Math.cos(angle) * distance + 'px');
    particle.style.setProperty('--dy', Math.sin(angle) * distance + 'px');
    particle.style.animationDelay = Math.round(Math.random() * 150) + 'ms';
    particle.style.animationDuration = duration + 'ms';
    container.appendChild(particle);
    setTimeout(() => particle.remove(), duration + 150);
  }
  const bgPulse = document.createElement('div');
  bgPulse.style.cssText = 'position:fixed;inset:0;background:radial-gradient(circle at 50% 50%, rgba(109,179,63,0.1) 0%, transparent 70%);pointer-events:none;animation:bgPulse 600ms ease-out forwards;';
  document.body.appendChild(bgPulse);
  setTimeout(() => bgPulse.remove(), 600);
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
