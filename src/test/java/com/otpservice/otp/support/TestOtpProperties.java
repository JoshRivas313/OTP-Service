package com.otpservice.otp.support;

import com.otpservice.otp.adapter.config.OtpProperties;

// OtpProperties tiene muchos parametros posicionales: este constructor evita que cada test repita la lista completa.
// Por defecto: modo demo, sin limites y con los mismos intentos y bloqueo que la configuracion real.
public final class TestOtpProperties {

    private String hashSecret = "secreto";
    private boolean demoMode = true;
    private int rateLimitPerDestination = 0;
    private int rateLimitPerIp = 0;
    private int rateLimitWindowSeconds = 600;
    private int dailySendLimit = 0;
    private boolean logCodes = true;
    private boolean customMessageEnabled = false;
    private int memoryMaxEntries = 10000;

    private TestOtpProperties() {
    }

    public static TestOtpProperties otp() {
        return new TestOtpProperties();
    }

    public TestOtpProperties hashSecret(String value) {
        this.hashSecret = value;
        return this;
    }

    public TestOtpProperties demoMode(boolean value) {
        this.demoMode = value;
        return this;
    }

    public TestOtpProperties rateLimitPerDestination(int value) {
        this.rateLimitPerDestination = value;
        return this;
    }

    public TestOtpProperties rateLimitPerIp(int value) {
        this.rateLimitPerIp = value;
        return this;
    }

    public TestOtpProperties dailySendLimit(int value) {
        this.dailySendLimit = value;
        return this;
    }

    public TestOtpProperties logCodes(boolean value) {
        this.logCodes = value;
        return this;
    }

    public TestOtpProperties customMessageEnabled(boolean value) {
        this.customMessageEnabled = value;
        return this;
    }

    public TestOtpProperties memoryMaxEntries(int value) {
        this.memoryMaxEntries = value;
        return this;
    }

    public OtpProperties build() {
        return new OtpProperties(6, 30, ProtocolStack.MAX_ATTEMPTS, 86400, 2592000, hashSecret, demoMode, memoryMaxEntries,
                rateLimitPerDestination, rateLimitPerIp, rateLimitWindowSeconds, ProtocolStack.LOCK_SECONDS,
                0, 0, 0, dailySendLimit, logCodes, customMessageEnabled);
    }
}
