const ICON_OK = '<svg width="16" height="16" viewBox="0 0 24 24" fill="none"><path d="M20 6L9 17l-5-5" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"/></svg>';
const ICON_ERR = '<svg width="16" height="16" viewBox="0 0 24 24" fill="none"><path d="M18 6L6 18M6 6l12 12" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"/></svg>';

const TYPES = {
  TOTP: {
    formula: 'código = HMAC(secreto, ⌊hora ÷ ventana⌋) · RFC 6238',
    desc: 'Tu app y el servidor miran el reloj y llegan al mismo número, sin hablarse.',
    facts: [
      'Tu celular calcula este código sin internet: solo necesita el secreto del QR y la hora.',
      'Google Authenticator, Microsoft Authenticator y el 2FA de GitHub usan exactamente este cálculo.',
      'Si el reloj de tu celular va adelantado unos segundos, igual funciona: el servidor acepta la ventana vecina.'
    ]
  },
  HOTP: {
    formula: 'código = HMAC(secreto, contador) · RFC 4226',
    desc: 'Cada vez que pedís un código en la app su contador sube; el servidor acepta hasta 10 de adelanto.',
    facts: [
      'HOTP nació para llaveros sin reloj: el código no vence, solo se gasta.',
      'Si generás códigos en la app sin usarlos, el servidor igual te acepta el siguiente: tolera 10 de adelanto.',
      'Authy no admite HOTP; Google Authenticator sí.'
    ]
  }
};

let factIndex = 0;

function showFact(advance) {
  const facts = TYPES[selectedType()].facts;
  factIndex = advance ? (factIndex + 1) % facts.length : 0;
  $('dyk-text').textContent = facts[factIndex];
}

const state = {
  email: null,
  type: 'TOTP',
  digits: 6,
  period: 30,
  secret: null,
  simCounter: 0,
  simTimer: null,
  simCode: null,
  step: 1
};

const $ = (id) => document.getElementById(id);

function validateEmail(email) {
  return email.length <= 254 && /^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}$/.test(email);
}

function showResult(el, ok, message) {
  el.innerHTML = (ok ? ICON_OK : ICON_ERR) + '<span></span>';
  el.querySelector('span').textContent = message;
  el.className = 'result show ' + (ok ? 'ok' : 'err');
}

function clearResult(el) {
  el.className = 'result';
  el.innerHTML = '';
}

async function call(method, url, body, button, resultEl) {
  button.disabled = true;
  button.classList.add('loading');
  button.setAttribute('aria-busy', 'true');
  try {
    const response = await fetch(url, {
      method,
      headers: body ? { 'Content-Type': 'application/json' } : {},
      body: body ? JSON.stringify(body) : undefined
    });
    const text = await response.text();
    const data = text ? JSON.parse(text) : {};
    if (resultEl) {
      showResult(resultEl, response.ok, data.message || (response.ok ? 'Listo' : data.code || 'Error'));
    }
    return { ok: response.ok, data };
  } catch (error) {
    if (resultEl) showResult(resultEl, false, 'No se pudo conectar con el servidor');
    return { ok: false, data: {} };
  } finally {
    button.disabled = false;
    button.classList.remove('loading');
    button.setAttribute('aria-busy', 'false');
  }
}

function showStep(number) {
  state.step = number;
  for (let i = 1; i <= 4; i++) {
    $('step-' + i).hidden = i !== number;
    const nav = $('nav-' + i);
    nav.classList.toggle('active', i === number);
    nav.classList.toggle('done', i < number);
  }
  $('simulator').hidden = number < 2 || !state.secret;
  const focusTarget = { 1: 'email', 3: 'confirm-code', 4: 'login-code' }[number];
  if (focusTarget) $(focusTarget).focus();
}

async function sendEmailCode() {
  const email = $('email').value.trim().toLowerCase();
  if (!validateEmail(email)) {
    showResult($('send-result'), false, 'Ingresá un correo válido');
    return;
  }
  const { ok, data } = await call('POST', '/api/email/otps', { email }, $('send-btn'), $('send-result'));
  if (!ok) return;
  state.email = email;
  $('email-code-box').hidden = false;
  $('email-demo').hidden = !data.demoCode;
  $('email-demo-value').textContent = data.demoCode || '';
  $('email-code').focus();
}

async function verifyEmailCode() {
  const code = $('email-code').value.trim();
  const { ok } = await call('POST', '/api/email/otps/verify', { email: state.email, code },
    $('email-verify-btn'), $('email-verify-result'));
  if (ok) {
    setTimeout(() => showStep(2), 500);
  }
}

function selectedType() {
  return document.querySelector('input[name="auth-type"]:checked').value;
}

function applyType() {
  const type = selectedType();
  $('type-formula').textContent = TYPES[type].formula;
  $('type-desc').textContent = TYPES[type].desc;
  $('period-field').hidden = type !== 'TOTP';
  showFact(false);
}

async function enroll() {
  const type = selectedType();
  const digits = parseInt($('digits').value, 10);
  const body = { email: state.email, type, digits };
  if (type === 'TOTP') body.periodSeconds = parseInt($('period').value, 10);

  const { ok, data } = await call('POST', '/api/authenticator/enrollments', body, $('enroll-btn'), $('enroll-result'));
  if (!ok) {
    if (data.code === 'EMAIL_NOT_VERIFIED') setTimeout(() => showStep(1), 1200);
    return;
  }
  state.type = data.type;
  state.digits = data.digits;
  state.period = data.periodSeconds || 30;
  state.secret = base32Decode(data.secretBase32);
  state.simCounter = data.counter || 0;

  $('qr-box').innerHTML = data.qrSvg;
  $('secret').textContent = data.secretBase32.replace(/(.{4})/g, '$1 ').trim();
  $('qr-section').hidden = false;
  $('confirm-hint').textContent = state.type === 'TOTP'
    ? 'Escribí el código que muestra tu app ahora.'
    : 'En tu app tocá la cuenta para generar el primer código y escribilo.';
  $('login-hint').textContent = state.type === 'TOTP'
    ? 'Ya no hace falta pedir nada: abrí tu app y escribí el código que muestra en este momento.'
    : 'Pedile un código nuevo a tu app (botón de recargar) y escribilo. El servidor no envía nada.';
  $('simulator').hidden = false;
  startSimulator();
}

async function copySecret() {
  try {
    await navigator.clipboard.writeText($('secret').textContent.replace(/\s/g, ''));
    $('copy-btn').textContent = 'Copiado';
    setTimeout(() => { $('copy-btn').textContent = 'Copiar'; }, 1500);
  } catch (error) {
    $('copy-btn').textContent = 'Copialo a mano';
  }
}

function codeBody(inputId) {
  return { email: state.email, type: state.type, code: $(inputId).value.trim() };
}

async function confirmEnrollment() {
  const { ok } = await call('POST', '/api/authenticator/enrollments/confirm', codeBody('confirm-code'),
    $('confirm-btn'), $('confirm-result'));
  if (ok) {
    setTimeout(() => showStep(4), 600);
  }
}

async function login() {
  const { ok, data } = await call('POST', '/api/authenticator/verify', codeBody('login-code'),
    $('login-btn'), $('login-result'));
  if (!ok) return;
  $('login-code').value = '';
  $('login-receipt').hidden = false;
  $('rc-type').textContent = data.type === 'TOTP' ? 'TOTP · por tiempo' : 'HOTP · por contador';
  $('rc-value-label').textContent = data.type === 'TOTP' ? 'Ventana T aceptada' : 'Contador aceptado';
  $('rc-value').textContent = data.type === 'TOTP' ? data.timeStep : data.counter;
  $('rc-time').textContent = new Date().toLocaleTimeString('es-PE', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
}

async function removeEnrollment() {
  const params = new URLSearchParams({ email: state.email, type: state.type });
  const { ok } = await call('DELETE', '/api/authenticator/enrollments?' + params, null, $('remove-btn'), $('login-result'));
  if (!ok) return;
  showResult($('login-result'), true, 'App desvinculada. Borrá también la cuenta en tu app.');
  stopSimulator();
  state.secret = null;
  setTimeout(() => {
    $('qr-section').hidden = true;
    $('qr-box').innerHTML = '';
    clearResult($('enroll-result'));
    showStep(2);
  }, 1500);
}

function base32Decode(value) {
  const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';
  const bytes = [];
  let buffer = 0;
  let bits = 0;
  for (const char of value.replace(/=+$/, '')) {
    buffer = (buffer << 5) | alphabet.indexOf(char);
    bits += 5;
    if (bits >= 8) {
      bytes.push((buffer >>> (bits - 8)) & 0xff);
      bits -= 8;
    }
  }
  return new Uint8Array(bytes);
}

async function hotp(secret, counter, digits) {
  const key = await crypto.subtle.importKey('raw', secret, { name: 'HMAC', hash: 'SHA-1' }, false, ['sign']);
  const message = new DataView(new ArrayBuffer(8));
  message.setUint32(0, Math.floor(counter / 0x100000000));
  message.setUint32(4, counter >>> 0);
  const hash = new Uint8Array(await crypto.subtle.sign('HMAC', key, message.buffer));
  const offset = hash[hash.length - 1] & 0x0f;
  const binary = ((hash[offset] & 0x7f) << 24) | (hash[offset + 1] << 16) | (hash[offset + 2] << 8) | hash[offset + 3];
  return String(binary % 10 ** digits).padStart(digits, '0');
}

function formatCode(code) {
  const half = Math.ceil(code.length / 2);
  return code.slice(0, half) + ' ' + code.slice(half);
}

async function renderSimulator() {
  if (!state.secret) return;
  if (state.type === 'TOTP') {
    const now = Date.now() / 1000;
    const step = Math.floor(now / state.period);
    const remaining = state.period - (now % state.period);
    state.simCode = await hotp(state.secret, step, state.digits);
    $('sim-meta').textContent = 'Ventana T = ' + step + ' · cambia en ' + Math.ceil(remaining) + ' s';
    $('sim-bar').style.width = (remaining / state.period * 100) + '%';
  } else {
    state.simCode = await hotp(state.secret, state.simCounter, state.digits);
    $('sim-meta').textContent = 'Contador = ' + state.simCounter;
  }
  $('sim-code').textContent = formatCode(state.simCode);
}

function startSimulator() {
  stopSimulator();
  const isTotp = state.type === 'TOTP';
  $('sim-timer').hidden = !isTotp;
  $('sim-next-btn').hidden = isTotp;
  renderSimulator();
  if (isTotp) {
    state.simTimer = setInterval(renderSimulator, 500);
  }
}

function stopSimulator() {
  clearInterval(state.simTimer);
  state.simTimer = null;
}

function useSimulatedCode() {
  const target = state.step === 3 ? 'confirm-code' : state.step === 4 ? 'login-code' : null;
  if (!target || !state.simCode) return;
  $(target).value = state.simCode;
  $(target).focus();
}

async function checkStorage() {
  try {
    const health = await (await fetch('/health')).json();
    $('memory-warning').hidden = health.storage !== 'memory';
  } catch (error) {
    $('memory-warning').hidden = true;
  }
}

function onEnter(inputId, action) {
  $(inputId).addEventListener('keydown', (event) => {
    if (event.key === 'Enter') action();
  });
}

['email-code', 'confirm-code', 'login-code'].forEach((id) => {
  $(id).addEventListener('input', () => {
    $(id).value = $(id).value.replace(/\D/g, '');
  });
});

$('send-btn').addEventListener('click', sendEmailCode);
$('email-verify-btn').addEventListener('click', verifyEmailCode);
$('enroll-btn').addEventListener('click', enroll);
$('copy-btn').addEventListener('click', copySecret);
$('to-confirm-btn').addEventListener('click', () => showStep(3));
$('back-to-qr-btn').addEventListener('click', () => showStep(2));
$('confirm-btn').addEventListener('click', confirmEnrollment);
$('login-btn').addEventListener('click', login);
$('remove-btn').addEventListener('click', removeEnrollment);
$('restart-btn').addEventListener('click', () => window.location.reload());
$('sim-next-btn').addEventListener('click', () => {
  state.simCounter++;
  renderSimulator();
});
$('sim-use-btn').addEventListener('click', useSimulatedCode);
$('dyk-next').addEventListener('click', () => showFact(true));
document.querySelectorAll('input[name="auth-type"]').forEach((radio) => radio.addEventListener('change', applyType));

onEnter('email', sendEmailCode);
onEnter('email-code', verifyEmailCode);
onEnter('confirm-code', confirmEnrollment);
onEnter('login-code', login);

applyType();
checkStorage();
