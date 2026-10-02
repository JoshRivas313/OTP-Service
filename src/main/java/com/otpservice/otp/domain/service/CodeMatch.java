package com.otpservice.otp.domain.service;

public record CodeMatch(Outcome outcome, long value) {

    // SUPERSEDED: HOTP emitido y sin usar que quedo fuera de la ventana por tener codigos mas nuevos detras.
    public enum Outcome { MATCH, REUSED, EXPIRED, SUPERSEDED, NO_MATCH }

    public static CodeMatch match(long value) {
        return new CodeMatch(Outcome.MATCH, value);
    }

    public static CodeMatch reused() {
        return new CodeMatch(Outcome.REUSED, -1);
    }

    public static CodeMatch expired() {
        return new CodeMatch(Outcome.EXPIRED, -1);
    }

    public static CodeMatch superseded() {
        return new CodeMatch(Outcome.SUPERSEDED, -1);
    }

    public static CodeMatch noMatch() {
        return new CodeMatch(Outcome.NO_MATCH, -1);
    }

    public boolean matched() {
        return outcome == Outcome.MATCH;
    }
}
