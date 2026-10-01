package com.otpservice.otp.adapter.config;

import com.otpservice.otp.adapter.exception.RateLimitExceededException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

// Ventana deslizante en memoria, por destino y por IP. Un valor menor o igual a 0 desactiva ese limite.
@Component
@RequiredArgsConstructor
public class SendRateLimiter {

    private static final int MAX_TRACKED_KEYS = 20_000;

    private final OtpProperties properties;
    private final Clock clock;
    private final Map<String, Deque<Instant>> byDestination = new HashMap<>();
    private final Map<String, Deque<Instant>> byIp = new HashMap<>();

    public synchronized void check(String destination, String ip) {
        Instant now = clock.instant();
        Instant since = now.minusSeconds(properties.rateLimitWindowSeconds());

        Deque<Instant> destinationHits = hitsFor(byDestination, destination, since);
        Deque<Instant> ipHits = hitsFor(byIp, ip, since);

        if (isFull(destinationHits, properties.rateLimitPerDestination())
                || isFull(ipHits, properties.rateLimitPerIp())) {
            throw new RateLimitExceededException();
        }
        destinationHits.addLast(now);
        ipHits.addLast(now);
    }

    private Deque<Instant> hitsFor(Map<String, Deque<Instant>> tracked, String key, Instant since) {
        if (tracked.size() > MAX_TRACKED_KEYS) {
            tracked.values().removeIf(hits -> hits.isEmpty() || hits.peekLast().isBefore(since));
        }
        Deque<Instant> hits = tracked.computeIfAbsent(key, k -> new ArrayDeque<>());
        while (!hits.isEmpty() && hits.peekFirst().isBefore(since)) {
            hits.pollFirst();
        }
        return hits;
    }

    private boolean isFull(Deque<Instant> hits, int max) {
        return max > 0 && hits.size() >= max;
    }
}
