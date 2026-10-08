package com.otpservice.otp.adapter.config;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;

// Tope global de envios en las ultimas 24 horas, para no agotar la cuota diaria del proveedor (el plan gratuito de Brevo
// da 300 correos al dia). Un limite menor o igual a 0 lo desactiva. Los limites por IP no lo reemplazan: muchas IP suman.
final class DailySendQuota {

    private static final long WINDOW_SECONDS = 24 * 3600L;

    private final Clock clock;
    private final int limit;
    private final Deque<Instant> sends = new ArrayDeque<>();

    DailySendQuota(Clock clock, int limit) {
        this.clock = clock;
        this.limit = limit;
    }

    synchronized void release() {
        if (!sends.isEmpty()) {
            sends.pollLast();
        }
    }

    synchronized boolean tryAcquire() {
        if (limit <= 0) {
            return true;
        }
        Instant now = clock.instant();
        Instant since = now.minusSeconds(WINDOW_SECONDS);
        while (!sends.isEmpty() && sends.peekFirst().isBefore(since)) {
            sends.pollFirst();
        }
        if (sends.size() >= limit) {
            return false;
        }
        sends.addLast(now);
        return true;
    }
}
