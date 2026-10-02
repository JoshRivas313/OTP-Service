package com.otpservice.otp.application.protocol;

import com.otpservice.otp.application.config.HmacSettings;
import com.otpservice.otp.application.config.OtpSettings;
import com.otpservice.otp.application.exception.OtpNotFoundException;
import com.otpservice.otp.application.port.out.CredentialPersistencePort;
import com.otpservice.otp.application.port.out.SecretCipherPort;
import com.otpservice.otp.domain.exception.InvalidCodeRequestException;
import com.otpservice.otp.domain.exception.InvalidOtpException;
import com.otpservice.otp.domain.exception.OtpAlreadyUsedException;
import com.otpservice.otp.domain.exception.OtpBlockedException;
import com.otpservice.otp.domain.exception.OtpExpiredException;
import com.otpservice.otp.domain.exception.OtpInvalidatedException;
import com.otpservice.otp.domain.model.HmacCredential;
import com.otpservice.otp.domain.service.CodeMatch;
import com.otpservice.otp.domain.service.HmacOtpAlgorithm;
import com.otpservice.otp.domain.valueobject.Destination;
import com.otpservice.otp.domain.valueobject.HmacSecret;
import com.otpservice.otp.domain.valueobject.HmacType;
import com.otpservice.otp.domain.valueobject.Purpose;

import java.time.Clock;
import java.time.Instant;

// HOTP y TOTP comparten el secreto cifrado, el bloqueo por intentos y el orden de la verificacion;
// cada uno aporta como calcula, como compara y como consume el codigo.
// Politica de intentos: emitir un codigo nunca reinicia los fallos ni levanta un bloqueo. Los fallos vuelven a cero
// solo con una verificacion correcta o al bloquearse; el bloqueo termina solo cuando vence lockedUntil.
public abstract class HmacCodeProtocol implements CodeProtocol {

    private final CredentialPersistencePort persistencePort;
    private final SecretCipherPort cipher;
    private final OtpSettings otpSettings;
    private final Clock clock;
    protected final HmacSettings settings;

    protected HmacCodeProtocol(CredentialPersistencePort persistencePort, SecretCipherPort cipher,
                               HmacSettings settings, OtpSettings otpSettings, Clock clock) {
        this.persistencePort = persistencePort;
        this.cipher = cipher;
        this.settings = settings;
        this.otpSettings = otpSettings;
        this.clock = clock;
    }

    protected abstract HmacType type();

    // HOTP avanza el contador en cada emision; TOTP no usa contador, depende solo de T = floor(unix / ventana).
    protected abstract boolean advancesCounter();

    protected abstract void ensurePeriodSupported(int periodSeconds);

    protected abstract IssuedCode codeFor(HmacCredential credential, byte[] secret, Instant now,
                                          int periodSeconds, int digits);

    protected abstract CodeMatch match(HmacCredential credential, byte[] secret, String code, Instant now);

    protected abstract boolean claim(HmacCredential credential, long matched);

    protected abstract VerifiedCode verifiedFor(long matched);

    protected final CredentialPersistencePort persistence() {
        return persistencePort;
    }

    @Override
    public final IssuedCode issue(Destination destination, Purpose purpose, int digits, int periodSeconds) {
        ensureDigitsSupported(digits);
        ensurePeriodSupported(periodSeconds);
        Instant now = clock.instant();
        HmacType type = type();
        String context = HmacCredential.secretContext(destination.getValue(), type, purpose);
        HmacCredential credential = persistencePort.createIfAbsent(HmacCredential.create(
                destination.getValue(), type, purpose, cipher.encrypt(HmacSecret.generate(), context), digits,
                periodSeconds, now));
        if (credential.isLocked(now)) {
            throw new OtpBlockedException();
        }
        credential = persistencePort.issue(credential.getId(), digits, type.effectivePeriod(periodSeconds),
                advancesCounter());
        byte[] secret = cipher.decrypt(credential.getSecret(), credential.secretContext()).bytes();
        return codeFor(credential, secret, now, periodSeconds, digits);
    }

    @Override
    public final VerifiedCode verify(Destination destination, Purpose purpose, String code) {
        HmacCredential credential = persistencePort.find(destination.getValue(), type(), purpose)
                .orElseThrow(OtpNotFoundException::new);
        Instant now = clock.instant();
        if (credential.isLocked(now)) {
            throw new OtpBlockedException();
        }

        byte[] secret = cipher.decrypt(credential.getSecret(), credential.secretContext()).bytes();
        CodeMatch match = match(credential, secret, code, now);

        return switch (match.outcome()) {
            case REUSED -> throw new OtpAlreadyUsedException();
            case EXPIRED -> throw new OtpExpiredException();
            case SUPERSEDED -> throw new OtpInvalidatedException();
            case NO_MATCH -> throw registerFailure(credential, now);
            case MATCH -> consume(credential, match.value());
        };
    }

    private VerifiedCode consume(HmacCredential credential, long matched) {
        if (!claim(credential, matched)) {
            throw new OtpAlreadyUsedException();
        }
        return verifiedFor(matched);
    }

    private RuntimeException registerFailure(HmacCredential credential, Instant now) {
        int attempts = persistencePort.registerFailure(credential.getId());
        int max = otpSettings.maxAttempts();
        if (attempts >= max) {
            persistencePort.lock(credential.getId(), now.plusSeconds(otpSettings.lockSeconds()));
            return new OtpBlockedException();
        }
        return InvalidOtpException.attempt(attempts, max);
    }

    private void ensureDigitsSupported(int digits) {
        if (digits < HmacOtpAlgorithm.MIN_DIGITS || digits > HmacOtpAlgorithm.MAX_DIGITS) {
            throw new InvalidCodeRequestException("%s usa entre %d y %d dígitos"
                    .formatted(type(), HmacOtpAlgorithm.MIN_DIGITS, HmacOtpAlgorithm.MAX_DIGITS));
        }
    }
}
