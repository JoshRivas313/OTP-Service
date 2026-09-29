const ICON_OK = '<svg width="16" height="16" viewBox="0 0 24 24" fill="none"><path d="M20 6L9 17l-5-5" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"/></svg>';
const ICON_ERR = '<svg width="16" height="16" viewBox="0 0 24 24" fill="none"><path d="M18 6L6 18M6 6l12 12" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"/></svg>';

function validatePeruvianMobile(national) {
  return /^9\d{8}$/.test(national);
}

function maskPhone(national) {
  return '+51 ' + national.slice(0, 2) + '*'.repeat(national.length - 4) + national.slice(-2);
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

const cellphoneInput = document.getElementById('gen-cellphone');
cellphoneInput.addEventListener('input', () => {
  cellphoneInput.value = cellphoneInput.value.replace(/\D/g, '');
  clearFieldError('gen-cellphone');
});
cellphoneInput.addEventListener('blur', () => {
  if (cellphoneInput.value && !validatePeruvianMobile(cellphoneInput.value)) {
    showFieldError('gen-cellphone', 'Ingresá 9 dígitos que empiecen con 9');
  }
});

const customToggle = document.getElementById('gen-custom-toggle');
const messageInput = document.getElementById('gen-message');
let messageEdited = false;

function purposeMessage() {
  return PURPOSES[selectedPurposeKey()].sms;
}

function sampleSeconds() {
  return document.getElementById('gen-expiration').value;
}

function refreshMessagePreview() {
  const sample = purposeMessage().replace('{code}', '123456').replace('{seconds}', sampleSeconds());
  document.getElementById('gen-message-preview').textContent = '"' + sample + '"';
  if (!messageEdited) messageInput.value = purposeMessage();
}

customToggle.addEventListener('change', () => {
  document.getElementById('gen-message-box').hidden = !customToggle.checked;
  document.getElementById('gen-message-default').hidden = customToggle.checked;
  if (customToggle.checked) messageInput.focus();
});
document.querySelectorAll('input[name="purpose"]').forEach(radio => radio.addEventListener('change', refreshMessagePreview));
document.getElementById('gen-expiration').addEventListener('change', refreshMessagePreview);
messageInput.addEventListener('input', () => {
  messageEdited = true;
  document.getElementById('gen-message').classList.remove('error');
  const help = document.querySelector('#gen-message-box .field-error');
  if (help) help.remove();
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
let lastCellphone = null;
let lastDurationSeconds = null;
let lastPurpose = 'login';
let lastMaskedPhone = '';
let otpExpired = false;

// El propósito solo cambia el texto de la demo: el backend valida el código igual en todos los casos.
const PURPOSES = {
  login: {
    sms: 'Tu código para iniciar sesión es {code}. Vence en {seconds} segundos.',
    action: 'Inicio de sesión',
    verifyHint: 'Confirmá que sos vos para iniciar sesión.',
    title: 'Acceso concedido',
    desc: 'Comprobamos que tenés este celular. En una app real, acá entrarías a tu cuenta.'
  },
  signup: {
    sms: 'Tu código para crear tu cuenta es {code}. Vence en {seconds} segundos.',
    action: 'Registro',
    verifyHint: 'Verificamos tu celular para crear tu cuenta.',
    title: 'Celular verificado',
    desc: 'Tu número es real y es tuyo. En una app real, acá seguirías con el registro.'
  },
  reset: {
    sms: 'Tu código para recuperar tu acceso es {code}. Vence en {seconds} segundos.',
    action: 'Recuperación de acceso',
    verifyHint: 'Confirmá tu identidad para recuperar el acceso.',
    title: 'Identidad confirmada',
    desc: 'En una app real, acá podrías elegir una contraseña nueva.'
  },
  payment: {
    sms: 'Tu código para confirmar tu pago es {code}. Vence en {seconds} segundos.',
    action: 'Confirmación de pago',
    verifyHint: 'Confirmá la operación con el código que te enviamos.',
    title: 'Operación autorizada',
    desc: 'Comprobamos que sos vos quien la aprueba. En una app real, acá se ejecutaría el pago.'
  }
};

function selectedPurposeKey() {
  const checked = document.querySelector('input[name="purpose"]:checked');
  return checked && PURPOSES[checked.value] ? checked.value : 'login';
}

function attachCodeBoxListeners() {
  const boxes = document.querySelectorAll('.code-box');
  boxes.forEach((box, index) => {
    box.addEventListener('input', (e) => {
      e.target.value = e.target.value.replace(/\D/g, '');
      e.target.classList.toggle('filled', e.target.value !== '');
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
          boxes[index + i].classList.add('filled');
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

function formatClock(totalSeconds) {
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return minutes + ':' + String(seconds).padStart(2, '0');
}

function resetTimerUi() {
  const bar = document.getElementById('timer-bar');
  const text = document.getElementById('timer-text');
  otpExpired = false;
  bar.style.transition = 'none';
  bar.style.width = '100%';
  void bar.offsetWidth;
  bar.style.transition = '';
  bar.classList.remove('warn');
  text.classList.remove('warn', 'expired');
  document.getElementById('timer-track').setAttribute('aria-valuenow', '100');
  document.getElementById('timer-display').textContent = '--:--';
  document.getElementById('otp-expired').classList.remove('show');
}

function expireTimerUi() {
  otpExpired = true;
  const bar = document.getElementById('timer-bar');
  bar.style.width = '0%';
  bar.classList.remove('warn');
  const text = document.getElementById('timer-text');
  text.classList.remove('warn');
  text.classList.add('expired');
  document.getElementById('timer-display').textContent = '0:00';
  document.getElementById('timer-track').setAttribute('aria-valuenow', '0');
  document.querySelectorAll('.code-box').forEach(box => {
    box.disabled = true;
  });
  document.getElementById('ver-btn').disabled = true;
  document.getElementById('otp-expired').classList.add('show');
}

// issuedAt = momento en que el servidor confirmó el envío, así el reloj no arranca tarde.
function startTimer(durationSeconds, issuedAt) {
  clearInterval(timerInterval);
  resetTimerUi();

  const totalMs = durationSeconds * 1000;
  const deadline = issuedAt + totalMs;
  const warnAt = Math.max(5, Math.round(durationSeconds * 0.25));
  const display = document.getElementById('timer-display');
  const bar = document.getElementById('timer-bar');
  const text = document.getElementById('timer-text');
  const track = document.getElementById('timer-track');
  let lastShown = null;

  const tick = () => {
    const msLeft = deadline - Date.now();
    const remaining = Math.max(0, Math.ceil(msLeft / 1000));

    if (remaining !== lastShown) {
      lastShown = remaining;
      display.textContent = formatClock(remaining);
      // La barra anima hacia donde estará en 1 s, así coincide con el tiempo real.
      bar.style.width = Math.max(0, ((msLeft - 1000) / totalMs) * 100) + '%';
      track.setAttribute('aria-valuenow', String(Math.round(Math.max(0, msLeft / totalMs) * 100)));
      const warn = remaining > 0 && remaining <= warnAt;
      bar.classList.toggle('warn', warn);
      text.classList.toggle('warn', warn);
    }

    if (msLeft <= 0) {
      clearInterval(timerInterval);
      expireTimerUi();
    }
  };

  tick();
  timerInterval = setInterval(tick, 250);
}

function showOtpStep(stepNumber) {
  document.querySelectorAll('.otp-step').forEach(step => {
    step.style.display = 'none';
  });
  const step = document.getElementById('otp-step-' + stepNumber);
  if (step) step.style.display = 'block';
}

function showMessageError(text) {
  const box = document.getElementById('gen-message-box');
  const existing = box.querySelector('.field-error');
  if (existing) existing.remove();
  const errorEl = document.createElement('span');
  errorEl.className = 'field-error';
  errorEl.textContent = text;
  box.appendChild(errorEl);
}

function resetOtpFlow(keepNumber = false) {
  showOtpStep(1);
  resetProgress();
  clearInterval(timerInterval);
  lastCellphone = null;
  lastDurationSeconds = null;
  if (!keepNumber) {
    document.getElementById('gen-cellphone').value = '';
  }
  clearFieldError('gen-cellphone');
  document.getElementById('gen-result').className = 'result';
  document.getElementById('gen-result').innerHTML = '';
  document.getElementById('gen-success').style.display = 'none';
  document.getElementById('ver-phone-display').textContent = '••••••••';
  document.getElementById('gen-message').classList.remove('error');
  document.getElementById('ver-result').className = 'result';
  document.getElementById('ver-result').innerHTML = '';
  document.querySelectorAll('.code-box').forEach(box => {
    box.value = '';
    box.classList.remove('error', 'filled');
    box.disabled = false;
  });
  resetTimerUi();
  document.getElementById('ver-btn').disabled = true;
}

async function generateOtp() {
  const national = cellphoneInput.value.trim();
  const cellphone = '+51' + national;

  if (!national) {
    showFieldError('gen-cellphone', 'Número requerido');
    return;
  }

  if (!validatePeruvianMobile(national)) {
    showFieldError('gen-cellphone', 'Ingresá 9 dígitos que empiecen con 9');
    return;
  }

  clearFieldError('gen-cellphone');
  const digits = parseInt(document.getElementById('gen-digits').value, 10);
  const durationSeconds = parseInt(document.getElementById('gen-expiration').value, 10);
  const message = customToggle.checked ? messageInput.value.trim() : purposeMessage();
  if (!message.includes('{code}')) {
    messageInput.classList.add('error');
    showMessageError('El mensaje debe incluir {code}');
    return;
  }
  const payload = { cellphone, digits, durationSeconds, message };

  const ok = await submit('gen-btn', 'gen-result', '/api/twilio/otps', payload);
  if (ok) {
    const issuedAt = Date.now();
    lastCellphone = cellphone;
    lastDurationSeconds = durationSeconds;
    lastPurpose = selectedPurposeKey();
    resetProgress();
    setProgressStep(1, 'done');

    document.getElementById('gen-success').style.display = 'flex';
    const displayPhone = maskPhone(national);
    document.getElementById('gen-phone-display').textContent = displayPhone;
    lastMaskedPhone = displayPhone;
    document.getElementById('ver-phone-display').textContent = displayPhone;
    document.getElementById('ver-purpose-desc').textContent = PURPOSES[lastPurpose].verifyHint;

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
      startTimer(durationSeconds, issuedAt);
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

  if (!lastCellphone) {
    return;
  }

  const ok = await submit('ver-btn', 'ver-result', '/api/twilio/otps/verify', { cellphone: lastCellphone, code });
  if (ok) {
    clearInterval(timerInterval);
    document.querySelectorAll('.code-box').forEach(box => box.disabled = true);
    setProgressStep(2, 'done');
    setProgressStep(3, 'done');
    renderVerifiedResult();
    setTimeout(() => {
      showOtpStep(3);
    }, 300);
  } else {
    if (otpExpired) {
      document.getElementById('ver-btn').disabled = true;
    }
    document.querySelectorAll('.code-box').forEach(box => box.classList.add('error'));
    setTimeout(() => {
      document.querySelectorAll('.code-box').forEach(box => box.classList.remove('error'));
    }, 300);
  }
}

function renderVerifiedResult() {
  const purpose = PURPOSES[lastPurpose] || PURPOSES.login;
  document.getElementById('ok-title').textContent = purpose.title;
  document.getElementById('ok-desc').textContent = purpose.desc;
  document.getElementById('rc-purpose').textContent = purpose.action;
  document.getElementById('rc-phone').textContent = lastMaskedPhone;
  document.getElementById('rc-time').textContent = new Date().toLocaleTimeString('es-PE', {
    hour: '2-digit', minute: '2-digit', second: '2-digit'
  });
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

function showVerifiedHint(status) {
  const hint = document.getElementById('gen-verified-hint');
  if (!status.restrictedToVerifiedNumbers) {
    hint.hidden = true;
    return;
  }
  hint.textContent = status.verifiedNumbers.length
    ? 'Tu cuenta de Twilio solo puede enviar a los números que verificaste: ' + status.verifiedNumbers.join(', ') + '.'
    : 'Tu cuenta de Twilio no tiene números verificados. Verificá tu celular en la consola de Twilio para poder enviar.';
  hint.hidden = false;
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
      showVerifiedHint(status);
    }
  } catch (error) {
  }
}

async function changeTwilioConfig() {
  await fetch('/api/twilio/disconnect', { method: 'POST' });
  window.location.href = '/';
}

checkTwilioBanner();

refreshMessagePreview();
