(() => {
const RFC_SECRET = new TextEncoder().encode('12345678901234567890');
const TOTP_PERIOD = 30;
const OTP_LIFETIME = 30;
const DIGITS = 6;
const REDUCED_MOTION = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

// --- Aletas -------------------------------------------------------------------

function buildFlaps(container) {
  const count = parseInt(container.dataset.flaps, 10);
  container.innerHTML = '';
  for (let i = 0; i < count; i++) {
    const flap = document.createElement('span');
    flap.className = 'flap';
    flap.innerHTML = '<span class="flap-top"><span>&nbsp;</span></span>'
      + '<span class="flap-bottom"><span>&nbsp;</span></span>'
      + '<span class="flap-leaf flap-leaf-top"><span>&nbsp;</span></span>'
      + '<span class="flap-leaf flap-leaf-bottom"><span>&nbsp;</span></span>';
    container.appendChild(flap);
  }
}

function setStatic(flap, ch) {
  flap.dataset.ch = ch;
  flap.querySelectorAll(':scope > span > span').forEach((face) => { face.textContent = ch; });
}

const FLIP_MS = 70;

// Un giro: la hoja superior cae mostrando el carácter viejo y la inferior aparece con el nuevo.
function flipOnce(flap, next) {
  return new Promise((resolve) => {
    const current = flap.dataset.ch || ' ';
    flap.querySelector('.flap-top > span').textContent = next;
    flap.querySelector('.flap-leaf-top > span').textContent = current;
    flap.querySelector('.flap-leaf-bottom > span').textContent = next;
    flap.classList.remove('is-flipping');
    void flap.offsetWidth;
    flap.classList.add('is-flipping');
    setTimeout(() => {
      flap.classList.remove('is-flipping');
      setStatic(flap, next);
      resolve();
    }, FLIP_MS * 2);
  });
}

// Como un tablero real, cada aleta pasa por algunos caracteres antes de detenerse.
async function flipTo(flap, target, spins) {
  if (REDUCED_MOTION) {
    setStatic(flap, target);
    return;
  }
  for (let i = 0; i < spins; i++) {
    await flipOnce(flap, String(Math.floor(Math.random() * 10)));
  }
  await flipOnce(flap, target);
}

function showCode(container, code) {
  const flaps = container.querySelectorAll('.flap');
  container.setAttribute('aria-label', 'Código ' + code.split('').join(' '));
  return Promise.all(Array.from(flaps).map((flap, i) =>
    new Promise((resolve) => setTimeout(() => flipTo(flap, code[i], 2 + (i % 3)).then(resolve), i * 55))));
}

// --- Cálculo (RFC 4226 / 6238) --------------------------------------------------

let hmacKey = null;

async function hotp(counter) {
  if (!hmacKey) {
    hmacKey = await crypto.subtle.importKey('raw', RFC_SECRET, { name: 'HMAC', hash: 'SHA-1' }, false, ['sign']);
  }
  const message = new DataView(new ArrayBuffer(8));
  message.setUint32(0, Math.floor(counter / 0x100000000));
  message.setUint32(4, counter >>> 0);
  const hash = new Uint8Array(await crypto.subtle.sign('HMAC', hmacKey, message.buffer));
  const offset = hash[hash.length - 1] & 0x0f;
  const binary = ((hash[offset] & 0x7f) << 24) | (hash[offset + 1] << 16) | (hash[offset + 2] << 8) | hash[offset + 3];
  return String(binary % 10 ** DIGITS).padStart(DIGITS, '0');
}

function randomCode() {
  const values = crypto.getRandomValues(new Uint32Array(DIGITS));
  return Array.from(values, (v) => String(v % 10)).join('');
}

// --- Filas --------------------------------------------------------------------

const rows = {};
document.querySelectorAll('.board-row').forEach((row) => {
  const flaps = row.querySelector('.flaps');
  buildFlaps(flaps);
  rows[row.dataset.protocol] = {
    row,
    flaps,
    previous: row.querySelector('.previous'),
    statusText: row.querySelector('.status-text'),
    field: (name) => row.querySelector('[data-field="' + name + '"]')
  };
});

function setStatus(protocol, text, state) {
  const r = rows[protocol];
  r.statusText.textContent = text;
  r.row.dataset.state = state;
}

function showPrevious(protocol, html) {
  const previous = rows[protocol].previous;
  previous.innerHTML = html;
  previous.hidden = false;
}

const otp = { code: null, issuedAt: 0 };
const hotpState = { counter: 0, code: null };
const totpState = { step: null, code: null, history: [] };
let flashUntil = 0;

// --- Política del servidor -------------------------------------------------------
// La tolerancia de TOTP y los demás límites salen de /api/otp-policy: los textos usan los mismos valores que la
// verificación. Mientras no llegan (o si fallan), no se afirma nada sobre la tolerancia.

let policy = null;

function groupDigits(value) {
  return String(value).replace(/\B(?=(\d{3})+(?!\d))/g, '\u202f');
}

function describePolicy() {
  const k = policy.totpToleranceSteps;
  const neighbours = k === 0 ? 'ninguna' : (k === 1 ? 'T−1 y T+1' : 'T−' + k + ' a T+' + k);
  const values = {
    hotpLookAhead: String(policy.hotpLookAhead),
    maxAttempts: String(policy.maxAttempts),
    lockMinutes: String(Math.round(policy.lockSeconds / 60)),
    totpTolerance: neighbours,
    totpLifetime: k * TOTP_PERIOD + '–' + (k + 1) * TOTP_PERIOD + ' s'
  };
  document.querySelectorAll('[data-policy]').forEach((node) => {
    const value = values[node.dataset.policy];
    if (value !== undefined) node.textContent = value;
  });
}

async function loadPolicy() {
  try {
    const response = await fetch('/api/otp-policy');
    if (!response.ok) return;
    policy = await response.json();
    describePolicy();
  } catch (e) {
    policy = null;
  }
}

async function newOtp() {
  const previous = otp.code;
  otp.code = randomCode();
  otp.issuedAt = Date.now();
  if (previous) {
    showPrevious('OTP', 'Anterior <span class="mono struck">' + previous + '</span> <span class="tag tag-void">Anulado</span>');
  }
  await showCode(rows.OTP.flaps, otp.code);
}

async function nextHotp(advance) {
  const previous = hotpState.code;
  if (advance) hotpState.counter++;
  hotpState.code = await hotp(hotpState.counter);
  rows.HOTP.field('counter').textContent = String(hotpState.counter);
  if (advance && previous) {
    // El anterior sigue dentro de la ventana de pendientes salvo que esa ventana sea de un solo código.
    const stillValid = !policy || policy.hotpLookAhead > 1;
    showPrevious('HOTP', 'Anterior <span class="mono' + (stillValid ? '' : ' struck') + '">' + previous + '</span> '
      + (stillValid
        ? '<span class="tag">Sigue valiendo hasta que uses uno posterior</span>'
        : '<span class="tag tag-void">Reemplazado</span>'));
  }
  setStatus('HOTP', 'No vence por tiempo', 'active');
  await showCode(rows.HOTP.flaps, hotpState.code);
}

async function refreshTotp(force) {
  const step = Math.floor(Date.now() / 1000 / TOTP_PERIOD);
  if (!force && step === totpState.step) return false;
  const changed = totpState.step !== null && step !== totpState.step;
  if (changed && totpState.code) {
    totpState.history.unshift({ step: totpState.step, code: totpState.code });
    totpState.history = totpState.history.slice(0, historySize());
  }
  totpState.step = step;
  totpState.code = await hotp(step);
  rows.TOTP.field('step').textContent = groupDigits(step);
  renderPreviousWindow(Date.now());
  await showCode(rows.TOTP.flaps, totpState.code);
  return true;
}

// Se muestran las ventanas que la tolerancia todavía acepta y la primera que ya no. Una ventana s se acepta mientras
// la actual no supere s + tolerancia, igual que TotpVerifier; sin la política no se afirma nada sobre la tolerancia.
function historySize() {
  return policy ? policy.totpToleranceSteps + 1 : 1;
}

function previousWindowLine(entry, now) {
  const label = 'Ventana T−' + (totpState.step - entry.step) + ': ';
  if (!policy) {
    return label + '<span class="mono">' + entry.code + '</span>';
  }
  const acceptedUntil = (entry.step + policy.totpToleranceSteps + 1) * TOTP_PERIOD * 1000;
  const left = Math.ceil((acceptedUntil - now) / 1000);
  if (left > 0) {
    return label + '<span class="mono">' + entry.code + '</span> <span class="mini-status">'
      + '<span class="lamp lamp-on" aria-hidden="true"></span>Tolerancia · ' + left + ' s</span>';
  }
  return label + '<span class="mono struck">' + entry.code + '</span> <span class="mini-status">'
    + '<span class="lamp" aria-hidden="true"></span>Vencida</span>';
}

function renderPreviousWindow(now) {
  if (!totpState.history.length) return;
  showPrevious('TOTP', totpState.history
    .map((entry) => '<span class="previous-line">' + previousWindowLine(entry, now) + '</span>')
    .join(''));
}

// Pedir otro dentro de la misma ventana: las aletas giran y vuelven a caer en el mismo número.
async function askTotpAgain() {
  const changedWindow = await refreshTotp(false);
  if (!changedWindow) {
    await showCode(rows.TOTP.flaps, totpState.code);
    flashUntil = Date.now() + 3500;
    setStatus('TOTP', 'Mismo código: misma ventana', 'flash');
  }
}

function tick() {
  const now = Date.now();
  const clock = document.getElementById('clock');
  clock.textContent = new Date(now).toLocaleTimeString('es-PE', { hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false });

  const seconds = now / 1000;
  const left = TOTP_PERIOD - (seconds % TOTP_PERIOD);
  rows.TOTP.field('left').textContent = String(Math.ceil(left));
  rows.TOTP.field('bar').style.transform = 'scaleX(' + (left / TOTP_PERIOD) + ')';
  if (now > flashUntil) setStatus('TOTP', 'Ventana actual', 'active');
  renderPreviousWindow(now);
  refreshTotp(false);

  if (otp.code) {
    const otpLeft = Math.max(0, Math.ceil(OTP_LIFETIME - (now - otp.issuedAt) / 1000));
    if (otpLeft > 0) {
      setStatus('OTP', 'Vence en ' + otpLeft + ' s', 'active');
    } else {
      setStatus('OTP', 'Vencido', 'void');
    }
  }
}

const live = document.getElementById('board-live');
const spell = (code) => code.split('').join(' ');

function announce(protocol) {
  if (protocol === 'OTP') {
    live.textContent = 'OTP aleatorio: nuevo código ' + spell(otp.code) + '. El anterior quedó anulado.';
  } else if (protocol === 'HOTP') {
    live.textContent = 'HOTP: contador ' + hotpState.counter + ', código ' + spell(hotpState.code)
      + '. El anterior sigue valiendo hasta que uses uno posterior.';
  } else {
    live.textContent = 'TOTP: ' + spell(totpState.code)
      + (Date.now() < flashUntil ? '. Mismo código, porque sigues en la misma ventana.' : '.');
  }
}

document.querySelectorAll('.key[data-action="otro"]').forEach((key) => {
  key.addEventListener('click', async () => {
    const protocol = key.closest('.board-row').dataset.protocol;
    key.disabled = true;
    if (protocol === 'OTP') await newOtp();
    if (protocol === 'HOTP') await nextHotp(true);
    if (protocol === 'TOTP') await askTotpAgain();
    announce(protocol);
    key.disabled = false;
  });
});

// --- Arranque -------------------------------------------------------------------

async function start() {
  if (!window.crypto || !crypto.subtle) {
    document.querySelector('.board-note').textContent =
      'Tu navegador no permite calcular los códigos de demostración en esta página.';
    return;
  }
  await loadPolicy();
  await Promise.all([newOtp(), nextHotp(false), refreshTotp(true)]);
  tick();
  setInterval(tick, 250);
}

start();
})();
