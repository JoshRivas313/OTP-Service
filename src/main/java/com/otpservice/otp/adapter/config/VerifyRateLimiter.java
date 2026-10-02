package com.otpservice.otp.adapter.config;

import com.otpservice.otp.adapter.exception.RateLimitExceededException;
import org.springframework.stereotype.Component;

import java.time.Clock;

// Limita las verificaciones, independiente de los intentos por codigo: frena la fuerza bruta que reparte
// intentos entre codigos nuevos o entre destinos. Cuenta cada verificacion, correcta o no.
@Component
public class VerifyRateLimiter {

    private final SlidingWindowLimiter limiter;

    public VerifyRateLimiter(OtpProperties properties, Clock clock) {
        this.limiter = new SlidingWindowLimiter(clock, properties.verifyRateLimitPerDestination(),
                properties.verifyRateLimitPerIp(), properties.rateLimitWindowSeconds());
    }

    public void check(String destination, String ip) {
        if (!limiter.tryAcquire(destination, ip)) {
            throw RateLimitExceededException.verifying();
        }
    }
}
