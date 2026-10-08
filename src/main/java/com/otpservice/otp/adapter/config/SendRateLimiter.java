package com.otpservice.otp.adapter.config;

import com.otpservice.otp.adapter.exception.RateLimitExceededException;
import org.springframework.stereotype.Component;

import java.time.Clock;

// Limita los envios de codigos: por destino y por IP en una ventana, y en total por dia (otp.daily-send-limit).
// La IP es HttpServletRequest.getRemoteAddr(): la resuelve Tomcat (ver README, "IP del cliente").
@Component
public class SendRateLimiter {

    private final SlidingWindowLimiter limiter;
    private final DailySendQuota dailyQuota;

    public SendRateLimiter(OtpProperties properties, Clock clock) {
        this.limiter = new SlidingWindowLimiter(clock, properties.rateLimitPerDestination(),
                properties.rateLimitPerIp(), properties.rateLimitWindowSeconds());
        this.dailyQuota = new DailySendQuota(clock, properties.dailySendLimit());
    }

    // Un envio que el proveedor no pudo entregar no es un abuso: se devuelven las plazas que gasto.
    public void refund(String destination, String ip) {
        limiter.release(destination, ip);
        dailyQuota.release();
    }

    // Primero los limites por destino e IP: un envio rechazado por ellos no gasta cuota diaria.
    public void check(String destination, String ip) {
        if (!limiter.tryAcquire(destination, ip)) {
            throw RateLimitExceededException.sending();
        }
        if (!dailyQuota.tryAcquire()) {
            throw RateLimitExceededException.dailyQuota();
        }
    }
}
