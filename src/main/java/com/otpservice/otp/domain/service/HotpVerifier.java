package com.otpservice.otp.domain.service;

// El servidor emite los codigos, asi que sabe cuales existen: counter..issuedCounter-1 son los enviados sin usar.
// Se aceptan los maxPending mas recientes (la ventana de look-ahead del RFC 4226, seccion 7.4), nunca un contador
// que no se emitio. Usar uno consume ese y todos los anteriores.
public final class HotpVerifier {

    private HotpVerifier() {
    }

    public static CodeMatch verify(byte[] secret, String code, long counter, long issuedCounter, int maxPending,
                                   int digits) {
        long oldestAccepted = Math.max(counter, issuedCounter - maxPending);
        for (long candidate = oldestAccepted; candidate < issuedCounter; candidate++) {
            if (matches(secret, candidate, code, digits)) {
                return CodeMatch.match(candidate);
            }
        }
        // Emitidos y sin usar, pero con maxPending codigos mas nuevos detras: quedaron reemplazados.
        for (long candidate = counter; candidate < oldestAccepted; candidate++) {
            if (matches(secret, candidate, code, digits)) {
                return CodeMatch.superseded();
            }
        }
        for (long candidate = Math.max(0, counter - maxPending); candidate < counter; candidate++) {
            if (matches(secret, candidate, code, digits)) {
                return CodeMatch.reused();
            }
        }
        return CodeMatch.noMatch();
    }

    private static boolean matches(byte[] secret, long candidate, String code, int digits) {
        return HmacOtpAlgorithm.sameCode(HmacOtpAlgorithm.hotp(secret, candidate, digits), code);
    }
}
