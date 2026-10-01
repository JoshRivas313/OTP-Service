package com.otpservice.otp.domain.service;

public record CodeMatch(Outcome outcome, long value) {

    public enum Outcome { MATCH, REUSED, EXPIRED, NO_MATCH }

    public static CodeMatch match(long value) {
        return new CodeMatch(Outcome.MATCH, value);
    }

    public static CodeMatch reused() {
        return new CodeMatch(Outcome.REUSED, -1);
    }

    public static CodeMatch expired() {
        return new CodeMatch(Outcome.EXPIRED, -1);
    }

    public static CodeMatch noMatch() {
        return new CodeMatch(Outcome.NO_MATCH, -1);
    }

    public boolean matched() {
        return outcome == Outcome.MATCH;
    }
}
