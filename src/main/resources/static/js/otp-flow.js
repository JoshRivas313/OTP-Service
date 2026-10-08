const ICON_OK = '<svg width="16" height="16" viewBox="0 0 24 24" fill="none"><path d="M20 6L9 17l-5-5" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"/></svg>';
const ICON_ERR = '<svg width="16" height="16" viewBox="0 0 24 24" fill="none"><path d="M18 6L6 18M6 6l12 12" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"/></svg>';

const API = OTP_CHANNEL.api;

// El error queda unido al campo (aria-describedby, aria-invalid) y se anuncia al aparecer (role="alert").
function showFieldError(fieldId, message) {
  const input = document.getElementById(fieldId);
  const existingError = input.parentElement.querySelector('.field-error');
  if (existingError) existingError.remove();
  input.classList.add('error');
  if (message) {
    const errorEl = document.createElement('span');
    errorEl.className = 'field-error';
    errorEl.id = fieldId + '-error';
    errorEl.setAttribute('role', 'alert');
    errorEl.textContent = message;
    input.parentElement.appendChild(errorEl);
    input.setAttribute('aria-invalid', 'true');
    input.setAttribute('aria-describedby', errorEl.id);
  }
}

function clearFieldError(fieldId) {
  const input = document.getElementById(fieldId);
  const existingError = input.parentElement.querySelector('.field-error');
  if (existingError) existingError.remove();
  input.classList.remove('error');
  input.removeAttribute('aria-invalid');
  input.removeAttribute('aria-describedby');
}

const customToggle = document.getElementById('gen-custom-toggle');
const messageInput = document.getElementById('gen-message');
let messageEdited = false;

// Solo para la vista previa: replica el texto que escribe el servidor (OtpMessage) segun el proposito.
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
document.querySelectorAll('input[name="purpose"]').forEach(radio => radio.addEventListener('change', () => {
  refreshMessagePreview();
  updateOptionsSummary();
}));
document.getElementById('gen-expiration').addEventListener('change', () => {
  renderTimeHelp();
  refreshMessagePreview();
});
document.getElementById('gen-digits').addEventListener('change', renderTimeHelp);
document.querySelectorAll('input[name="otp-type"]').forEach(radio => radio.addEventListener('change', applyType));
document.getElementById('dyk-next').addEventListener('click', () => showFact(true));
document.getElementById('resend-btn').addEventListener('click', resendCode);
messageInput.addEventListener('input', () => {
  messageEdited = true;
  document.getElementById('gen-message').classList.remove('error');
  const help = document.querySelector('#gen-message-box .field-error');
  if (help) help.remove();
});

// Los pasos anteriores quedan hechos y el actual se enciende; al verificar, el 3 queda hecho y encendido.
function showStepsAt(current, finished = false) {
  [1, 2, 3].forEach(number => {
    const step = document.getElementById('progress-step-' + number);
    const dot = step.querySelector('.otp-progress-dot');
    const done = number < current || (finished && number === current);
    const isCurrent = number === current;
    step.classList.toggle('done', done);
    dot.classList.toggle('done', done);
    step.classList.toggle('current', isCurrent);
    dot.classList.toggle('current', isCurrent);
    if (isCurrent) {
      step.setAttribute('aria-current', 'step');
    } else {
      step.removeAttribute('aria-current');
    }
  });
}

function resetProgress() {
  showStepsAt(1);
}

resetProgress();

let lastDigits = 6;
let timerInterval = null;
let lastDestination = null;
let lastDemoCode = null;
let lastDurationSeconds = null;
let lastPurpose = 'login';
let lastMaskedTarget = '';
let otpExpired = false;

// api es el valor que exige el backend: un código pedido para un propósito no sirve para otro.
const PURPOSES = {
  login: {
    api: 'LOGIN',
    sms: 'Tu código para iniciar sesión es {code}. Vence en {seconds} segundos.',
    action: 'Inicio de sesión',
    verifyHint: 'Confirma que eres tú para iniciar sesión.',
    title: 'Acceso concedido',
    desc: 'Comprobamos que el código te llegó a ti. En una app real, aquí entrarías a tu cuenta.'
  },
  signup: {
    api: 'REGISTER',
    sms: 'Tu código para crear tu cuenta es {code}. Vence en {seconds} segundos.',
    action: 'Registro',
    verifyHint: 'Verificamos que eres tú para crear tu cuenta.',
    title: 'Verificación completa',
    desc: 'El código era válido y solo lo tenías tú. En una app real, aquí seguirías con el registro.'
  },
  reset: {
    api: 'PASSWORD_RECOVERY',
    sms: 'Tu código para recuperar tu acceso es {code}. Vence en {seconds} segundos.',
    action: 'Recuperación de acceso',
    verifyHint: 'Confirma tu identidad para recuperar el acceso.',
    title: 'Identidad confirmada',
    desc: 'En una app real, aquí podrías elegir una contraseña nueva.'
  },
  payment: {
    api: 'PAYMENT_CONFIRMATION',
    sms: 'Tu código para confirmar tu pago es {code}. Vence en {seconds} segundos.',
    action: 'Confirmación de pago',
    verifyHint: 'Confirma la operación con el código que te enviamos.',
    title: 'Operación autorizada',
    desc: 'Comprobamos que eres tú quien la aprueba. En una app real, aquí se ejecutaría el pago.'
  }
};

const TYPES = {
  OTP: {
    formula: 'código = número aleatorio (SecureRandom)',
    desc: 'El servidor guarda el hash del código. Caduca por tiempo y sirve una sola vez.',
    expirationLabel: 'Expira en',
    expirations: [[30, '30 segundos'], [60, '60 segundos'], [120, '2 minutos'], [300, '5 minutos']],
    digits: [4, 10],
    receipt: 'OTP aleatorio',
    expirationHelp: () => 'Cuenta desde el momento en que lo pides: el reloj arranca con tu pedido.',
    example: (seconds, now) => 'Si lo pides ahora, vence a las ' + clockTime(now + seconds * 1000)
      + ' (justo ' + seconds + ' s).',
    digitsHelp: 'De 4 a 10 dígitos: el OTP aleatorio no sigue ningún RFC.',
    clockNote: 'Cuenta desde que lo pediste. Si pides otro, este deja de servir.',
    how: 'Comparó con el hash que guardó al enviarlo',
    facts: [
      'Un código de 6 dígitos tiene un millón de combinaciones: por eso se bloquea al tercer intento fallido.',
      'El servidor no guarda tu código en claro, solo su HMAC: ni quien vea la base de datos puede leerlo.',
      'Pedir un código nuevo invalida el anterior, aunque todavía no haya vencido.'
    ]
  },
  HOTP: {
    formula: 'código = Truncar(HMAC-SHA1(secreto, contador)) mod 10^dígitos · RFC 4226',
    desc: 'No caduca por tiempo: sirve hasta que lo uses. El servidor no guarda el código, solo el secreto y el contador.',
    expirationLabel: null,
    expirations: [],
    digits: [6, 8],
    receipt: 'HOTP · secreto + contador',
    expirationHelp: () => 'HOTP no tiene reloj: el código vale hasta que lo uses o uses uno posterior.',
    example: () => 'Si lo pides ahora, no vence: espera hasta que lo uses.',
    digitsHelp: 'De 6 a 8 dígitos, como pide el RFC 4226.',
    clockNote: 'Sin reloj: vale hasta que lo uses. Si pides otro, este sigue sirviendo hasta que uses uno posterior.',
    how: 'Recalculó Truncar(HMAC-SHA1(secreto, contador)); no guardó el código',
    facts: [
      'HOTP nació para llaveros sin reloj: el código no vence, solo se gasta al usarlo.',
      'Si pides tres códigos, los tres sirven… hasta que usas uno: los anteriores quedan anulados. Pruébalo.',
      'El servidor no guarda este código: cuando lo escribes, lo vuelve a calcular con el secreto y el contador.'
    ]
  },
  TOTP: {
    formula: 'código = HOTP(secreto, T) · T = ⌊hora Unix ÷ ventana⌋ · RFC 6238',
    desc: 'Vale hasta que terminan su ventana de tiempo y la tolerancia. Se recalcula con la hora; no se guarda.',
    expirationLabel: 'Ventana de tiempo',
    expirations: [[30, '30 segundos (estándar)'], [60, '60 segundos']],
    digits: [6, 8],
    receipt: 'TOTP · secreto + hora',
    expirationHelp: (period) => 'El código cambia cada ' + period + ' s según la hora, no desde que lo pides. '
      + 'Vale hasta que cierran su ventana y la tolerancia.',
    // Misma regla que TotpVerifier: se acepta mientras la ventana actual no supere T + la tolerancia del servidor.
    example: (period, now) => {
      const step = Math.floor(now / 1000 / period);
      if (!policy) {
        return 'Si lo pides ahora (ventana T = ' + step + '), su ventana cierra a las '
          + clockTime((step + 1) * period * 1000) + '.';
      }
      const closes = (step + 1 + policy.totpToleranceSteps) * period * 1000;
      return 'Si lo pides ahora (ventana T = ' + step + '), vale hasta las ' + clockTime(closes)
        + ': te quedan ' + Math.ceil((closes - now) / 1000) + ' s, no siempre ' + period + '.';
    },
    digitsHelp: 'De 6 a 8 dígitos, como pide el RFC 6238.',
    clockNote: 'Si pides otro dentro de la misma ventana, llega el mismo número.',
    how: 'Recalculó HOTP(secreto, T) para la ventana actual y su tolerancia; no guardó el código',
    facts: [
      'Google Authenticator y el 2FA de GitHub usan TOTP: el mismo cálculo que hace este servidor.',
      'Si pides dos códigos dentro de la misma ventana de 30 s, llega el mismo número. Pruébalo.',
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
}

// Reglas vigentes del servidor (tolerancia de TOTP, etc.). Sin ellas no se promete ninguna tolerancia.
let policy = null;
fetch('/api/otp-policy')
  .then(response => (response.ok ? response.json() : null))
  .then(loaded => {
    policy = loaded;
    renderTimeHelp();
    applyCustomMessagePolicy();
  })
  .catch(() => {});

// El texto del mensaje lo decide el servidor. La casilla para escribir uno propio solo aparece si el servidor lo permite
// (modo demo o OTP_CUSTOM_MESSAGE_ENABLED); si la politica no llega, se queda oculta.
let customMessageAllowed = false;

function applyCustomMessagePolicy() {
  customMessageAllowed = !!(policy && policy.customMessageEnabled);
  document.getElementById('custom-toggle-row').hidden = !customMessageAllowed;
  if (!customMessageAllowed) {
    customToggle.checked = false;
    document.getElementById('gen-message-box').hidden = true;
    document.getElementById('gen-message-default').hidden = false;
  }
}

// Lo que el visitante eligió dentro de "Opciones", visible sin abrirlas.
function updateOptionsSummary() {
  const type = selectedType();
  const seconds = parseInt(document.getElementById('gen-expiration').value, 10);
  const duration = type === 'HOTP' ? 'no vence' : (type === 'TOTP' ? 'ventana de ' + seconds + ' s' : 'vence en ' + seconds + ' s');
  const digits = document.getElementById('gen-digits').value;
  document.getElementById('options-summary').textContent =
    PURPOSES[selectedPurposeKey()].action + ' · ' + digits + ' dígitos · ' + duration;
}

function clockTime(millis) {
  return new Date(millis).toLocaleTimeString('es-PE', { hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false });
}

// La regla del tiempo, un ejemplo con la hora real y el rango de dígitos del tipo de OTP elegido.
function renderTimeHelp() {
  const type = TYPES[selectedType()];
  const seconds = parseInt(document.getElementById('gen-expiration').value, 10);
  updateOptionsSummary();
  document.getElementById('expiration-help').textContent = type.expirationHelp(seconds) + ' ' + type.digitsHelp;
  document.getElementById('expiration-example').textContent = type.example(seconds, Date.now());
}

setInterval(() => {
  if (!document.getElementById('expiration-example').closest('[hidden]')) renderTimeHelp();
}, 1000);

function applyType() {
  const type = TYPES[selectedType()];
  document.getElementById('type-formula').textContent = type.formula;
  document.getElementById('type-desc').textContent = type.desc;
  document.getElementById('totp-channel-note').hidden = selectedType() !== 'TOTP';

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

  renderTimeHelp();
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
    // La primera casilla recibe el codigo completo cuando el telefono lo autocompleta desde el SMS.
    box.maxLength = i === 0 ? count : 1;
    box.autocomplete = i === 0 ? 'one-time-code' : 'off';
    box.dataset.index = String(i);
    box.setAttribute('aria-label', 'Dígito ' + (i + 1));
    wrapper.appendChild(box);
  }
  attachCodeBoxListeners();
}

// Reparte varios digitos (pegado o autocompletado del SMS) desde una casilla, sin pasarse de la ultima.
function fillFrom(boxes, start, digits) {
  const list = digits.split('');
  boxes.forEach((box, i) => {
    if (i < start || i >= start + list.length) return;
    box.value = list[i - start];
    box.classList.add('filled');
  });
  if (list.length > 0) {
    boxes[Math.min(start + list.length - 1, boxes.length - 1)].focus();
  }
  updateVerifyButtonState();
}

function attachCodeBoxListeners() {
  const boxes = document.querySelectorAll('.code-box');
  boxes.forEach((box, index) => {
    box.addEventListener('focus', () => box.select());
    box.addEventListener('input', (e) => {
      const digits = e.target.value.replace(/\D/g, '');
      if (digits.length > 1) {
        fillFrom(boxes, index, digits);
        return;
      }
      e.target.value = digits;
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
      fillFrom(boxes, index, paste.replace(/\D/g, ''));
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

// moveFocus: al cambiar de paso el foco pasa al titulo del paso nuevo, para que un lector de pantalla sepa que cambio.
function showOtpStep(stepNumber, moveFocus = false) {
  document.querySelectorAll('.otp-step').forEach(step => {
    step.style.display = 'none';
  });
  const step = document.getElementById('otp-step-' + stepNumber);
  if (!step) return;
  document.body.dataset.step = String(stepNumber);
  step.style.display = 'block';
  const heading = step.querySelector('h2');
  if (moveFocus && heading) {
    heading.tabIndex = -1;
    heading.focus();
  }
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
  OTP_CHANNEL.resetTarget(keepNumber);
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
  const target = OTP_CHANNEL.readTarget();
  if (!target) return;

  const type = selectedType();
  const digits = parseInt(document.getElementById('gen-digits').value, 10);
  const durationSeconds = type === 'HOTP' ? null : parseInt(document.getElementById('gen-expiration').value, 10);
  const useCustomMessage = customMessageAllowed && customToggle.checked;
  const message = useCustomMessage ? messageInput.value.trim() : null;
  if (useCustomMessage && !message.includes('{code}')) {
    messageInput.classList.add('error');
    showMessageError('El mensaje debe incluir {code}');
    return;
  }
  if (OTP_CHANNEL.readyToSend && !OTP_CHANNEL.readyToSend()) {
    return;
  }
  const purpose = PURPOSES[selectedPurposeKey()].api;
  const payload = { [API.key]: target.value, type, purpose, digits, durationSeconds, message };

  const ok = await submit('gen-btn', 'gen-result', API.generate, payload);
  if (ok) {
    const issuedAt = Date.now();
    lastPayload = payload;
    lastDestination = target.value;
    lastType = type;
    lastPurpose = selectedPurposeKey();
    showStepsAt(2);

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

// Pedir otro con el mismo tipo de OTP muestra en vivo la diferencia entre los tres.
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
        + ': sigues en la ventana T = ' + current.timeStep + '. TOTP no sortea el código, lo calcula con la hora.';
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

  const ok = await submit('ver-btn', 'ver-result', API.verify, { [API.key]: lastDestination, type: lastType, purpose: lastPayload.purpose, code });
  if (ok) {
    clearInterval(timerInterval);
    document.querySelectorAll('.code-box').forEach(box => box.disabled = true);
    showStepsAt(3, true);
    renderVerifiedResult();
    setTimeout(() => {
      showOtpStep(3, true);
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
    const data = await response.json().catch(() => ({}));
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
    showResult(resultId, ok, data.message || data.code || unexpectedErrorMessage(response.status));
  } catch (error) {
    showResult(resultId, false, 'No se pudo conectar con el servidor');
  } finally {
    btn.disabled = false;
    btn.classList.remove('loading');
    btn.setAttribute('aria-busy', 'false');
  }
  return ok;
}

// El texto va como texto, no como HTML: lo que llegue del servidor nunca se interpreta como marcado.
function showResult(elementId, ok, message, detail) {
  const el = document.getElementById(elementId);
  el.innerHTML = ok ? ICON_OK : ICON_ERR;
  const text = document.createElement('span');
  text.textContent = message;
  if (detail) {
    const extra = document.createElement('span');
    extra.className = 'result-detail';
    extra.textContent = detail;
    text.appendChild(extra);
  }
  el.appendChild(text);
  el.setAttribute('aria-live', ok ? 'polite' : 'assertive');
  el.className = 'result show ' + (ok ? 'ok' : 'err');
}

// Un error que no trae {code, message} (un proxy, un corte) igual se explica.
function unexpectedErrorMessage(status) {
  return 'El servidor respondió con un error (' + status + '). Intenta de nuevo en un momento.';
}

// Sin JavaScript en linea en el HTML (la politica de seguridad solo permite scripts de este origen).
document.getElementById('gen-btn').addEventListener('click', generateOtp);
// Modo demo: la pantalla ya muestra el código; con un toque se rellena y el foco queda en Verificar.
document.getElementById('demo-fill').addEventListener('click', () => {
  if (!lastDemoCode) return;
  fillFrom(Array.from(document.querySelectorAll('.code-box')), 0, lastDemoCode);
  document.getElementById('ver-btn').focus();
});
document.getElementById('ver-btn').addEventListener('click', verifyOtp);
document.querySelectorAll('[data-action="reset-keep"]').forEach(button => button.addEventListener('click', () => resetOtpFlow(true)));
document.querySelectorAll('[data-action="reset"]').forEach(button => button.addEventListener('click', () => resetOtpFlow()));

applyType();
OTP_CHANNEL.start();
