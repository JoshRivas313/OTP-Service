package com.otpservice.otp.application.config;

// Valores que los casos de uso necesitan del codigo de un solo uso. Los arma el adaptador de configuracion.
public record OtpSettings(int digits, int durationSeconds, int maxAttempts, int retentionSeconds,
                          String messageTemplate, boolean demoMode) {
}
