// Nombre visible. El del correo y el de la app autenticadora están en application.yaml.
const BRAND = {
  name: 'Un Solo Uso',
  tagline: 'OTP · HOTP · TOTP por SMS, correo o app'
};

document.querySelectorAll('[data-brand]').forEach((el) => { el.textContent = BRAND.name; });
document.querySelectorAll('[data-brand-tagline]').forEach((el) => { el.textContent = BRAND.tagline; });
document.title = document.title.replace(/^[^·]*?(?=( · |$))/, BRAND.name);
