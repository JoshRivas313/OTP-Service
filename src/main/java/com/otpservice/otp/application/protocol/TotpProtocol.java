package com.otpservice.otp.application.protocol;

import com.otpservice.otp.application.config.HmacSettings;
import com.otpservice.otp.application.config.OtpSettings;
import com.otpservice.otp.application.port.out.CredentialPersistencePort;
import com.otpservice.otp.application.port.out.SecretCipherPort;
import com.otpservice.otp.domain.exception.InvalidCodeRequestException;
import com.otpservice.otp.domain.model.HmacCredential;
import com.otpservice.otp.domain.service.CodeMatch;
import com.otpservice.otp.domain.service.HmacOtpAlgorithm;
import com.otpservice.otp.domain.service.TotpVerifier;
import com.otpservice.otp.domain.valueobject.HmacType;
import com.otpservice.otp.domain.valueobject.OtpProtocol;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

// RFC 6238: el codigo sale de la hora y cambia solo al cerrarse su ventana.
@Component
public class TotpProtocol extends HmacCodeProtocol {

    private static final int MIN_PERIOD_SECONDS = 15;
    private static final int MAX_PERIOD_SECONDS = 300;
    private static final int EXPIRED_LOOK_BACK_STEPS = 20;

    public TotpProtocol(CredentialPersistencePort persistencePort, SecretCipherPort cipher,
                        HmacSettings settings, OtpSettings otpSettings, Clock clock) {
        super(persistencePort, cipher, settings, otpSettings, clock);
    }

    @Override
    public OtpProtocol protocol() {
        return OtpProtocol.TOTP;
    }

    @Override
    public boolean expires() {
        return true;
    }

    @Override
    protected boolean advancesCounter() {
        return false;
    }

    @Override
    protected HmacType type() {
        return HmacType.TOTP;
    }

    @Override
    protected void ensurePeriodSupported(int periodSeconds) {
        if (periodSeconds < MIN_PERIOD_SECONDS || periodSeconds > MAX_PERIOD_SECONDS) {
            throw new InvalidCodeRequestException("La ventana de TOTP debe estar entre %d y %d segundos"
                    .formatted(MIN_PERIOD_SECONDS, MAX_PERIOD_SECONDS));
        }
    }

    @Override
    protected IssuedCode codeFor(HmacCredential credential, byte[] secret, Instant now, int periodSeconds,
                                 int digits) {
        long timeStep = HmacOtpAlgorithm.timeStep(now, periodSeconds);
        Instant expiresAt = Instant.ofEpochSecond((timeStep + 1 + settings.totpToleranceSteps()) * periodSeconds);
        return new IssuedCode(HmacOtpAlgorithm.totp(secret, now, periodSeconds, digits),
                Duration.between(now, expiresAt).toSeconds(), null, timeStep);
    }

    @Override
    protected CodeMatch match(HmacCredential credential, byte[] secret, String code, Instant now) {
        return TotpVerifier.verify(secret, code, now, credential.getPeriodSeconds(), credential.getDigits(),
                settings.totpToleranceSteps(), credential.getLastUsedTimeStep(), EXPIRED_LOOK_BACK_STEPS);
    }

    @Override
    protected boolean claim(HmacCredential credential, long matched) {
        return persistence().claimTimeStep(credential.getId(), matched);
    }

    @Override
    protected VerifiedCode verifiedFor(long matched) {
        return new VerifiedCode(null, matched);
    }
}
