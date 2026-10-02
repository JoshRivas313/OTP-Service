package com.otpservice.otp.application.config;

// Valores que los casos de uso necesitan del codigo de un solo uso. Los arma el adaptador de configuracion.
// retentionSeconds: cuanto se conserva un registro tras vencer. lockSeconds: cuanto dura un bloqueo por intentos.
public record OtpSettings(int digits, int durationSeconds, int maxAttempts, int retentionSeconds,
                          int lockSeconds, String messageTemplate, boolean demoMode) {
}
