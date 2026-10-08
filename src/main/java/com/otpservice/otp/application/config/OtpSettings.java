package com.otpservice.otp.application.config;

// Valores que los casos de uso necesitan del codigo de un solo uso. Los arma el adaptador de configuracion.
// retentionSeconds: cuanto se conserva un registro tras vencer. lockSeconds: cuanto dura un bloqueo por intentos.
// customMessageEnabled: deja que quien pide el codigo escriba el texto del mensaje.
public record OtpSettings(int digits, int durationSeconds, int maxAttempts, int retentionSeconds,
                          int lockSeconds, boolean demoMode, boolean customMessageEnabled) {

    // En modo demo nada se envia (la aplicacion no arranca si se combina con un proveedor real), asi que el texto libre
    // no puede llegar a nadie y se permite siempre.
    public boolean customMessageAllowed() {
        return customMessageEnabled || demoMode;
    }
}
