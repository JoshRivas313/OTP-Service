package com.otpservice.otp.adapter.config;

import com.otpservice.otp.adapter.exception.RateLimitExceededException;
import org.springframework.stereotype.Component;

import java.time.Clock;

// Limita las conexiones de Twilio por IP: cada una hace llamadas salientes a Twilio con credenciales que aporta el visitante.
// No hay destino que contar, asi que el limite por destino queda desactivado.
@Component
public class ConnectRateLimiter {

    private static final String NO_DESTINATION = "connect";

    private final SlidingWindowLimiter limiter;

    public ConnectRateLimiter(OtpProperties properties, Clock clock) {
        this.limiter = new SlidingWindowLimiter(clock, 0, properties.connectRateLimitPerIp(),
                properties.rateLimitWindowSeconds());
    }

    public void check(String ip) {
        if (!limiter.tryAcquire(NO_DESTINATION, ip)) {
            throw RateLimitExceededException.connecting();
        }
    }
}
