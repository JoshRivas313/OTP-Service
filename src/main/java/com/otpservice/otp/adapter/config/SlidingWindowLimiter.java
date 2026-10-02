package com.otpservice.otp.adapter.config;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

// Ventana deslizante en memoria, por destino y por IP. Un limite menor o igual a 0 lo desactiva.
// Solo cuenta el intento si ninguno de los dos limites esta lleno.
final class SlidingWindowLimiter {

    private static final int MAX_TRACKED_KEYS = 20_000;

    private final Clock clock;
    private final int perDestination;
    private final int perIp;
    private final int windowSeconds;
    private final Map<String, Deque<Instant>> byDestination = new HashMap<>();
    private final Map<String, Deque<Instant>> byIp = new HashMap<>();

    SlidingWindowLimiter(Clock clock, int perDestination, int perIp, int windowSeconds) {
        this.clock = clock;
        this.perDestination = perDestination;
        this.perIp = perIp;
        this.windowSeconds = windowSeconds;
    }

    synchronized boolean tryAcquire(String destination, String ip) {
        Instant now = clock.instant();
        Instant since = now.minusSeconds(windowSeconds);

        Deque<Instant> destinationHits = hitsFor(byDestination, destination, since);
        Deque<Instant> ipHits = hitsFor(byIp, ip, since);

        if (isFull(destinationHits, perDestination) || isFull(ipHits, perIp)) {
            return false;
        }
        destinationHits.addLast(now);
        ipHits.addLast(now);
        return true;
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

    private static boolean isFull(Deque<Instant> hits, int max) {
        return max > 0 && hits.size() >= max;
    }
}
