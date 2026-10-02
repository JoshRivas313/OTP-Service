package com.otpservice.otp.adapter.config;

import com.otpservice.otp.adapter.exception.RateLimitExceededException;
import org.springframework.stereotype.Component;

import java.time.Clock;

// Limita los envios de codigos. La IP es HttpServletRequest.getRemoteAddr(): la resuelve Tomcat (ver README, "IP del cliente").
@Component
public class SendRateLimiter {

    private final SlidingWindowLimiter limiter;

    public SendRateLimiter(OtpProperties properties, Clock clock) {
        this.limiter = new SlidingWindowLimiter(clock, properties.rateLimitPerDestination(),
                properties.rateLimitPerIp(), properties.rateLimitWindowSeconds());
    }

    public void check(String destination, String ip) {
        if (!limiter.tryAcquire(destination, ip)) {
            throw RateLimitExceededException.sending();
        }
    }
}
