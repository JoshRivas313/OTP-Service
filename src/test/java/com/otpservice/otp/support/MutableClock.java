package com.otpservice.otp.support;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

public final class MutableClock extends Clock {

    private volatile Instant now;

    public MutableClock(Instant now) {
        this.now = now;
    }

    public void plusSeconds(long seconds) {
        now = now.plusSeconds(seconds);
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return now;
    }
}
