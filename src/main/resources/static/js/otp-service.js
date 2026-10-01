const ICON_OK = '<svg width="16" height="16" viewBox="0 0 24 24" fill="none"><path d="M20 6L9 17l-5-5" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"/></svg>';
const ICON_ERR = '<svg width="16" height="16" viewBox="0 0 24 24" fill="none"><path d="M18 6L6 18M6 6l12 12" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"/></svg>';

const CHANNEL = new URLSearchParams(window.location.search).get('canal') === 'correo' ? 'email' : 'sms';
const API = CHANNEL === 'email'
  ? { generate: '/api/email/otps', verify: '/api/email/otps/verify', key: 'email' }
  : { generate: '/api/twilio/otps', verify: '/api/twilio/otps/verify', key: 'cellphone' };

function validateEmail(email) {
  return email.length <= 254 && /^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}$/.test(email);
}

function maskEmail(email) {
  return email[0] + '***' + email.slice(email.indexOf('@'));
}

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

const emailInput = document.getElementById('gen-email');
emailInput.addEventListener('input', () => clearFieldError('gen-email'));
emailInput.addEventListener('blur', () => {
  if (emailInput.value && !validateEmail(emailInput.value.trim().toLowerCase())) {
    showFieldError('gen-email', 'Ingresá un correo válido');
  }
});

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
  const message = PURPOSES[selectedPurposeKey()].sms;
  const type = selectedType();
  if (type === 'HOTP') return message.replace('Vence en {seconds} segundos.', 'Sirve hasta que lo uses.');
  if (type === 'TOTP') return message.replace('Vence en {seconds} segundos.', 'Vence en {seconds} segundos, al cerrar su ventana.');
  return message;
}

// En TOTP los segundos reales dependen de en qué momento de la ventana se pida el código.
function sampleSeconds() {
  const seconds = parseInt(document.getElementById('gen-expiration').value, 10);
  return selectedType() === 'TOTP' ? seconds + '–' + seconds * 2 : String(seconds);
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
document.getElementById('gen-expiration').addEventListener('change', () => {
  document.getElementById('expiration-help').textContent =
    TYPES[selectedType()].expirationHelp(parseInt(document.getElementById('gen-expiration').value, 10));
  refreshMessagePreview();
});
document.querySelectorAll('input[name="otp-type"]').forEach(radio => radio.addEventListener('change', applyType));
document.getElementById('dyk-next').addEventListener('click', () => showFact(true));
document.getElementById('resend-btn').addEventListener('click', resendCode);
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

let lastDigits = 6;
let timerInterval = null;
let lastDestination = null;
let lastDemoCode = null;
let lastDurationSeconds = null;
let lastPurpose = 'login';
let lastMaskedTarget = '';
let otpExpired = false;

// El propósito solo cambia el texto de la demo: el backend valida el código igual en todos los casos.
const PURPOSES = {
  login: {
    sms: 'Tu código para iniciar sesión es {code}. Vence en {seconds} segundos.',
    action: 'Inicio de sesión',
    verifyHint: 'Confirmá que sos vos para iniciar sesión.',
    title: 'Acceso concedido',
    desc: 'Comprobamos que el código llegó a vos. En una app real, acá entrarías a tu cuenta.'
  },
  signup: {
    sms: 'Tu código para crear tu cuenta es {code}. Vence en {seconds} segundos.',
    action: 'Registro',
    verifyHint: 'Verificamos que sos vos para crear tu cuenta.',
    title: 'Verificación completa',
    desc: 'El código era válido y solo lo tenías vos. En una app real, acá seguirías con el registro.'
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

const TYPES = {
  OTP: {
    formula: 'código = número aleatorio (SecureRandom)',
    desc: 'El servidor guarda el hash del código. Caduca por tiempo y sirve una sola vez.',
    expirationLabel: 'Expira en',
    expirations: [[30, '30 segundos'], [60, '60 segundos'], [120, '2 minutos'], [300, '5 minutos']],
    digits: [4, 10],
    receipt: 'OTP · aleatorio',
    expirationHelp: () => 'Cuenta desde el momento en que lo pedís.',
    clockNote: 'Cuenta desde que lo pediste. Si pedís otro, este deja de servir.',
    how: 'Comparó con el hash que guardó al enviarlo',
    facts: [
      'Un código de 6 dígitos tiene un millón de combinaciones: por eso se bloquea al tercer intento fallido.',
      'El servidor no guarda tu código en claro, solo su HMAC: ni quien vea la base de datos puede leerlo.',
      'Pedir un código nuevo invalida el anterior, aunque todavía no haya vencido.'
    ]
  },
  HOTP: {
    formula: 'código = HMAC(secreto, contador) · RFC 4226',
    desc: 'No caduca por tiempo: sirve hasta que lo uses. El servidor no guarda el código, solo el secreto y el contador.',
    expirationLabel: null,
    expirations: [],
    digits: [6, 8],
    receipt: 'HOTP · por contador',
    expirationHelp: () => 'HOTP no tiene reloj: el código vale hasta que lo uses.',
    clockNote: 'Sin reloj: vale hasta que lo uses. Si pedís otro, este sigue sirviendo hasta que uses uno posterior.',
    how: 'Recalculó HMAC(secreto, contador); no guardó el código',
    facts: [
      'HOTP nació para llaveros sin reloj: el código no vence, solo se gasta al usarlo.',
      'Si pedís tres códigos, los tres sirven… hasta que usás uno: los anteriores quedan anulados. Probalo.',
      'El servidor no guarda este código: cuando lo escribís, lo vuelve a calcular con el secreto y el contador.'
    ]
  },
  TOTP: {
    formula: 'código = HMAC(secreto, ⌊hora ÷ ventana⌋) · RFC 6238',
    desc: 'Vale hasta que termina su ventana de tiempo, más una de tolerancia. Se recalcula con la hora; no se guarda.',
    expirationLabel: 'Ventana de tiempo',
    expirations: [[30, '30 segundos (estándar)'], [60, '60 segundos']],
    digits: [6, 8],
    receipt: 'TOTP · por tiempo',
    expirationHelp: (period) => 'No es la vigencia exacta: el código vale hasta que cierra su ventana, más una de tolerancia (entre '
      + period + ' y ' + period * 2 + ' s).',
    clockNote: 'Si pedís otro dentro de la misma ventana, llega el mismo número.',
    how: 'Recalculó HMAC(secreto, ventana actual ±1); no guardó el código',
    facts: [
      'Google Authenticator y el 2FA de GitHub usan TOTP: el mismo cálculo que hace este servidor.',
      'Si pedís dos códigos dentro de la misma ventana de 30 s, llega el mismo número. Probalo.',
      'Un código TOTP vencido no gasta intentos: el servidor sabe que era correcto, pero llegó tarde.'
    ]
  }
};

let lastType = 'OTP';
let lastGenerateData = null;
let lastPayload = null;
let factIndex = 0;

function selectedType() {
  const checked = document.querySelector('input[name="otp-type"]:checked');
  return checked && TYPES[checked.value] ? checked.value : 'OTP';
}

function showFact(advance) {
  const type = selectedType();
  const facts = TYPES[type].facts;
  factIndex = advance ? (factIndex + 1) % facts.length : 0;
  document.getElementById('dyk-text').textContent = facts[factIndex];
  document.getElementById('dyk-app-link').hidden = type === 'OTP';
}

function applyType() {
  const type = TYPES[selectedType()];
  document.getElementById('type-formula').textContent = type.formula;
  document.getElementById('type-desc').textContent = type.desc;

  const field = document.getElementById('field-expiration');
  const select = document.getElementById('gen-expiration');
  field.hidden = !type.expirationLabel;
  if (type.expirationLabel) {
    document.getElementById('gen-expiration-label').textContent = type.expirationLabel;
    const previous = parseInt(select.value, 10);
    select.innerHTML = type.expirations
      .map(([seconds, label]) => '<option value="' + seconds + '">' + label + '</option>')
      .join('');
    select.value = type.expirations.some(([seconds]) => seconds === previous) ? previous : type.expirations[0][0];
  }

  const digits = document.getElementById('gen-digits');
  const [min, max] = type.digits;
  Array.from(digits.options).forEach(option => {
    const value = parseInt(option.value, 10);
    option.disabled = value < min || value > max;
  });
  const current = parseInt(digits.value, 10);
  if (current < min || current > max) digits.value = '6';

  document.getElementById('expiration-help').textContent = type.expirationHelp(parseInt(select.value, 10));
  showFact(false);
  refreshMessagePreview();
}

function selectedPurposeKey() {
  const checked = document.querySelector('input[name="purpose"]:checked');
  return checked && PURPOSES[checked.value] ? checked.value : 'login';
}

function buildCodeBoxes(count) {
  lastDigits = count;
  const wrapper = document.querySelector('.code-boxes-wrapper');
  wrapper.innerHTML = '';
  wrapper.style.setProperty('--code-len', count);
  wrapper.classList.toggle('long', count > 6);
  for (let i = 0; i < count; i++) {
    const box = document.createElement('input');
    box.className = 'code-box';
    box.type = 'text';
    box.inputMode = 'numeric';
    box.maxLength = 1;
    box.autocomplete = 'off';
    box.dataset.index = String(i);
    box.setAttribute('aria-label', 'Dígito ' + (i + 1));
    wrapper.appendChild(box);
  }
  attachCodeBoxListeners();
}

function attachCodeBoxListeners() {
  const boxes = document.querySelectorAll('.code-box');
  boxes.forEach((box, index) => {
    box.addEventListener('input', () => {
      if (Array.from(boxes).some(b => b.value)) {
        setProgressStep(2, 'active');
      }
    });
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
  btn.disabled = code.length < lastDigits;
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

function setTimerVisible(visible) {
  document.getElementById('timer-text').hidden = !visible;
  document.getElementById('timer-track').hidden = !visible;
}

function describeIssuedCode(data) {
  if (data.type === 'HOTP') return 'HOTP · contador ' + data.counter;
  if (data.type === 'TOTP') return 'TOTP · ventana T = ' + data.timeStep;
  return 'OTP · aleatorio';
}

let windowInterval = null;

function stopWindow() {
  clearInterval(windowInterval);
  windowInterval = null;
  document.getElementById('totp-window').hidden = true;
}

// El servidor fija T; el navegador solo lo hace avanzar con su reloj para mostrar cuándo cambia.
function startWindow(data, periodSeconds, issuedAt) {
  stopWindow();
  const panel = document.getElementById('totp-window');
  const stepEl = document.getElementById('totp-step');
  const leftEl = document.getElementById('totp-left');
  const bar = document.getElementById('totp-bar');
  const track = panel.querySelector('.totp-window-track');
  const offset = data.timeStep - Math.floor(issuedAt / 1000 / periodSeconds);
  const until = new Date(issuedAt + data.expiresInSeconds * 1000);
  document.getElementById('totp-until').textContent =
    until.toLocaleTimeString('es-PE', { hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false });
  panel.hidden = false;

  const tick = () => {
    const now = Date.now() / 1000;
    const step = Math.floor(now / periodSeconds) + offset;
    const left = periodSeconds - (now % periodSeconds);
    const inTolerance = step > data.timeStep;
    stepEl.textContent = 'T = ' + step + (inTolerance ? ' · tolerancia' : '');
    panel.classList.toggle('in-tolerance', inTolerance);
    leftEl.textContent = String(Math.ceil(left));
    bar.style.width = (left / periodSeconds * 100) + '%';
    track.setAttribute('aria-valuenow', String(Math.round(left / periodSeconds * 100)));
  };
  tick();
  windowInterval = setInterval(tick, 250);
}

function expireTimerUi() {
  otpExpired = true;
  stopWindow();
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
  lastDestination = null;
  lastDemoCode = null;
  lastGenerateData = null;
  document.getElementById('demo-code-box').hidden = true;
  document.getElementById('type-chip').hidden = true;
  document.getElementById('resend-note').hidden = true;
  stopWindow();
  setTimerVisible(true);
  lastDurationSeconds = null;
  if (!keepNumber) {
    document.getElementById('gen-cellphone').value = '';
    document.getElementById('gen-email').value = '';
  }
  clearFieldError('gen-cellphone');
  clearFieldError('gen-email');
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

function readTarget() {
  if (CHANNEL === 'email') {
    const email = emailInput.value.trim().toLowerCase();
    if (!email) {
      showFieldError('gen-email', 'Correo requerido');
      return null;
    }
    if (!validateEmail(email)) {
      showFieldError('gen-email', 'Ingresá un correo válido');
      return null;
    }
    clearFieldError('gen-email');
    return { value: email, masked: maskEmail(email) };
  }

  const national = cellphoneInput.value.trim();
  if (!national) {
    showFieldError('gen-cellphone', 'Número requerido');
    return null;
  }
  if (!validatePeruvianMobile(national)) {
    showFieldError('gen-cellphone', 'Ingresá 9 dígitos que empiecen con 9');
    return null;
  }
  clearFieldError('gen-cellphone');
  return { value: '+51' + national, masked: maskPhone(national) };
}

async function generateOtp() {
  const target = readTarget();
  if (!target) return;

  const type = selectedType();
  const digits = parseInt(document.getElementById('gen-digits').value, 10);
  const durationSeconds = type === 'HOTP' ? null : parseInt(document.getElementById('gen-expiration').value, 10);
  const message = customToggle.checked ? messageInput.value.trim() : purposeMessage();
  if (!message.includes('{code}')) {
    messageInput.classList.add('error');
    showMessageError('El mensaje debe incluir {code}');
    return;
  }
  const payload = { [API.key]: target.value, type, digits, durationSeconds, message };

  const ok = await submit('gen-btn', 'gen-result', API.generate, payload);
  if (ok) {
    const issuedAt = Date.now();
    lastPayload = payload;
    lastDestination = target.value;
    lastType = type;
    lastPurpose = selectedPurposeKey();
    resetProgress();
    setProgressStep(1, 'done');

    document.getElementById('gen-success').style.display = 'flex';
    document.getElementById('gen-phone-display').textContent = target.masked;
    lastMaskedTarget = target.masked;
    document.getElementById('ver-phone-display').textContent = target.masked;
    document.getElementById('ver-purpose-desc').textContent = PURPOSES[lastPurpose].verifyHint;
    document.getElementById('otp-expired-text').textContent = type === 'TOTP'
      ? 'Su ventana y la de tolerancia ya cerraron.'
      : 'El código expiró.';
    document.getElementById('clock-note').textContent = TYPES[type].clockNote;

    setTimeout(() => {
      showOtpStep(2);
      showIssuedCode(lastGenerateData, issuedAt);
      document.querySelector('.code-box').focus();
    }, 600);
  }
}

function showIssuedCode(data, issuedAt) {
  const demoBox = document.getElementById('demo-code-box');
  demoBox.hidden = !lastDemoCode;
  document.getElementById('demo-code-value').textContent = lastDemoCode || '';

  const chip = document.getElementById('type-chip');
  chip.textContent = data && data.type ? describeIssuedCode(data) : '';
  chip.hidden = !chip.textContent;

  buildCodeBoxes(lastPayload.digits);
  document.getElementById('ver-btn').disabled = true;
  clearInterval(timerInterval);
  resetTimerUi();
  stopWindow();
  lastDurationSeconds = data && data.expiresInSeconds ? data.expiresInSeconds : lastPayload.durationSeconds;

  if (lastType === 'HOTP') {
    setTimerVisible(false);
    return;
  }
  startTimer(lastDurationSeconds, issuedAt);
  if (lastType === 'TOTP' && data && data.timeStep != null) {
    setTimerVisible(false);
    startWindow(data, lastPayload.durationSeconds, issuedAt);
  } else {
    setTimerVisible(true);
  }
}

// Pedir otro con el mismo protocolo muestra en vivo la diferencia entre los tres.
async function resendCode() {
  if (!lastPayload) return;
  const previous = lastGenerateData;
  const previousCode = lastDemoCode;
  const ok = await submit('resend-btn', 'ver-result', API.generate, lastPayload, { quietSuccess: true });
  if (!ok) return;
  showIssuedCode(lastGenerateData, Date.now());
  const note = document.getElementById('resend-note');
  note.textContent = resendExplanation(previous, lastGenerateData, previousCode, lastDemoCode);
  note.hidden = false;
  document.querySelector('.code-box').focus();
}

function resendExplanation(previous, current, previousCode, currentCode) {
  if (current.type === 'TOTP') {
    const sameWindow = previous && previous.timeStep === current.timeStep;
    if (sameWindow) {
      return 'Llegó el mismo número' + (currentCode && currentCode === previousCode ? ' (' + currentCode + ')' : '')
        + ': seguís en la ventana T = ' + current.timeStep + '. TOTP no sortea el código, lo calcula con la hora.';
    }
    return 'La ventana ya cambió (T = ' + current.timeStep + '), por eso llegó un número distinto.';
  }
  if (current.type === 'HOTP') {
    return 'Contador ' + current.counter + ': llegó un número nuevo. El anterior sigue sirviendo hasta que uses uno posterior.';
  }
  return 'Llegó un código distinto, sorteado de nuevo. El anterior quedó anulado.';
}

async function verifyOtp() {
  const code = Array.from(document.querySelectorAll('.code-box')).map(b => b.value).join('').trim();

  if (!code || code.length < lastDigits) {
    document.querySelectorAll('.code-box').forEach(box => box.classList.add('error'));
    setTimeout(() => {
      document.querySelectorAll('.code-box').forEach(box => box.classList.remove('error'));
    }, 300);
    return;
  }

  if (!lastDestination) {
    return;
  }

  const ok = await submit('ver-btn', 'ver-result', API.verify, { [API.key]: lastDestination, type: lastType, code });
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
  document.getElementById('rc-type').textContent = TYPES[lastType].receipt;
  document.getElementById('rc-how').textContent = TYPES[lastType].how;
  document.getElementById('rc-phone').textContent = lastMaskedTarget;
  document.getElementById('rc-time').textContent = new Date().toLocaleTimeString('es-PE', {
    hour: '2-digit', minute: '2-digit', second: '2-digit'
  });
}

async function submit(buttonId, resultId, url, body, options = {}) {
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
    if (url === API.generate && ok) {
      lastDemoCode = data.demoCode || null;
      lastGenerateData = data;
    }

    if (ok && options.quietSuccess) {
      document.getElementById(resultId).className = 'result';
      document.getElementById(resultId).innerHTML = '';
      return ok;
    }
    if (ok && url === API.verify) {
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
  window.location.href = '/#sms';
}

function applyChannel() {
  if (CHANNEL !== 'email') {
    checkTwilioBanner();
    return;
  }
  document.getElementById('field-phone').hidden = true;
  document.getElementById('field-email').hidden = false;
  document.getElementById('gen-subtitle').textContent = 'Solicitá un código de verificación por correo';
  document.getElementById('gen-help').textContent = 'El código llega a tu correo real. Si no lo ves, revisá la carpeta de spam.';
  document.getElementById('gen-sent-label').textContent = 'Correo enviado a';
  document.getElementById('ver-target-label').textContent = 'Correo confirmado';
  document.getElementById('ver-back-btn').textContent = '← Cambiar correo';
  document.getElementById('rc-target-label').textContent = 'Correo verificado';
  document.getElementById('gen-message').setAttribute('aria-label', 'Mensaje del correo');
  document.getElementById('twilio-status-bar').style.display = 'flex';
  const back = document.getElementById('twilio-connect-link');
  back.textContent = '← Elegir otro canal';
  back.href = '/';
  back.style.display = 'inline';
}

applyChannel();

applyType();
