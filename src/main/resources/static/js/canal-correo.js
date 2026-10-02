// Canal de correo: validaciones del email y llamada a /api/email/otps.
const emailInput = document.getElementById('gen-email');

function validateEmail(email) {
  return email.length <= 254 && /^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}$/.test(email);
}

function maskEmail(email) {
  return email[0] + '***' + email.slice(email.indexOf('@'));
}

emailInput.addEventListener('input', () => clearFieldError('gen-email'));
emailInput.addEventListener('blur', () => {
  if (emailInput.value && !validateEmail(emailInput.value.trim().toLowerCase())) {
    showFieldError('gen-email', 'Ingresa un correo válido');
  }
});

window.OTP_CHANNEL = {
  api: { generate: '/api/email/otps', verify: '/api/email/otps/verify', key: 'email' },

  readTarget() {
    const email = emailInput.value.trim().toLowerCase();
    if (!email) {
      showFieldError('gen-email', 'Correo requerido');
      return null;
    }
    if (!validateEmail(email)) {
      showFieldError('gen-email', 'Ingresa un correo válido');
      return null;
    }
    clearFieldError('gen-email');
    return { value: email, masked: maskEmail(email) };
  },

  resetTarget(keepNumber) {
    if (!keepNumber) emailInput.value = '';
    clearFieldError('gen-email');
  },

  start() {}
};
