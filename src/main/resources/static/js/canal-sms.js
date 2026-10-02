// Canal SMS: celular peruano, conexión de la cuenta de Twilio del visitante y llamada a /api/twilio/otps.
const cellphoneInput = document.getElementById('gen-cellphone');
let twilioConnected = false;

function validatePeruvianMobile(national) {
  return /^9\d{8}$/.test(national);
}

function maskPhone(national) {
  return '+51 ' + national.slice(0, 2) + '*'.repeat(national.length - 4) + national.slice(-2);
}

cellphoneInput.addEventListener('input', () => {
  cellphoneInput.value = cellphoneInput.value.replace(/\D/g, '');
  clearFieldError('gen-cellphone');
});
cellphoneInput.addEventListener('blur', () => {
  if (cellphoneInput.value && !validatePeruvianMobile(cellphoneInput.value)) {
    showFieldError('gen-cellphone', 'Ingresa 9 dígitos que empiecen con 9');
  }
});

function showVerifiedHint(status) {
  const hint = document.getElementById('gen-verified-hint');
  if (!status.restrictedToVerifiedNumbers) {
    hint.hidden = true;
    return;
  }
  hint.textContent = status.verifiedNumbers.length
    ? 'Tu cuenta de Twilio solo puede enviar a los números que verificaste: ' + status.verifiedNumbers.join(', ') + '.'
    : 'Tu cuenta de Twilio no tiene números verificados. Verifica tu celular en la consola de Twilio para poder enviar.';
  hint.hidden = false;
}

// El paso 1 se ve siempre; el formulario de Twilio aparece solo al enviar sin una cuenta conectada.
function showTwilioConnect(show) {
  document.getElementById('sms-connect').hidden = !show;
  document.getElementById('otp-step-1').style.display = show ? 'none' : '';
}

function cancelTwilioConnect() {
  showTwilioConnect(false);
  document.getElementById('gen-btn').focus();
}

async function refreshTwilioStatus() {
  try {
    const status = await (await fetch('/api/twilio/status')).json();
    twilioConnected = status.connected;
    document.getElementById('twilio-status-bar').style.display = status.connected ? 'flex' : 'none';
    if (status.connected) {
      document.getElementById('twilio-connected-badge').style.display = 'flex';
      document.getElementById('twilio-disconnect-btn').style.display = 'inline';
      document.getElementById('tw-masked-inline').textContent = status.maskedCredentials;
      showVerifiedHint(status);
    }
  } catch (error) {
    twilioConnected = false;
  }
}

async function connectTwilio() {
  const btn = document.getElementById('tw-connect-btn');
  const label = btn.querySelector('.btn-label');
  const result = document.getElementById('tw-connect-result');
  btn.disabled = true;
  btn.classList.add('loading');
  label.textContent = 'Verificando credenciales...';
  result.className = 'result';
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
      await refreshTwilioStatus();
      showTwilioConnect(false);
      showResult('gen-result', true, 'Twilio conectado. Ya puedes enviar el código.');
      document.getElementById('gen-btn').focus();
      return;
    }
    showResult('tw-connect-result', false,
        'No pudimos validar estas credenciales.<span class="result-detail">' + (data.message || data.code) + '</span>');
  } catch (error) {
    showResult('tw-connect-result', false, 'No se pudo conectar con el servidor');
  } finally {
    btn.disabled = false;
    btn.classList.remove('loading');
    label.textContent = 'Conectar con Twilio';
  }
}

async function changeTwilioConfig() {
  await fetch('/api/twilio/disconnect', { method: 'POST' });
  twilioConnected = false;
  document.getElementById('twilio-status-bar').style.display = 'none';
  document.getElementById('gen-verified-hint').hidden = true;
  resetOtpFlow(true);
  showTwilioConnect(true);
  document.getElementById('tw-account-sid').focus();
}

window.OTP_CHANNEL = {
  api: { generate: '/api/twilio/otps', verify: '/api/twilio/otps/verify', key: 'cellphone' },

  readTarget() {
    const national = cellphoneInput.value.trim();
    if (!national) {
      showFieldError('gen-cellphone', 'Número requerido');
      return null;
    }
    if (!validatePeruvianMobile(national)) {
      showFieldError('gen-cellphone', 'Ingresa 9 dígitos que empiecen con 9');
      return null;
    }
    clearFieldError('gen-cellphone');
    return { value: '+51' + national, masked: maskPhone(national) };
  },

  // Sin Twilio no se puede enviar: se pide la cuenta y se conserva lo que el usuario ya eligió.
  readyToSend() {
    if (twilioConnected) {
      return true;
    }
    showTwilioConnect(true);
    document.getElementById('tw-account-sid').focus();
    return false;
  },

  resetTarget(keepNumber) {
    if (!keepNumber) cellphoneInput.value = '';
    clearFieldError('gen-cellphone');
  },

  start() {
    refreshTwilioStatus();
  }
};
