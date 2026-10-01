package com.otpservice.otp.application.protocol;

import com.otpservice.otp.application.config.HmacSettings;
import com.otpservice.otp.application.config.OtpSettings;
import com.otpservice.otp.application.port.out.CredentialPersistencePort;
import com.otpservice.otp.application.port.out.SecretCipherPort;
import com.otpservice.otp.domain.model.HmacCredential;
import com.otpservice.otp.domain.service.CodeMatch;
import com.otpservice.otp.domain.service.HmacOtpAlgorithm;
import com.otpservice.otp.domain.service.HotpVerifier;
import com.otpservice.otp.domain.valueobject.HmacType;
import com.otpservice.otp.domain.valueobject.OtpProtocol;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

// RFC 4226: el codigo sale de un contador y vale hasta que se use.
@Component
public class HotpProtocol extends HmacCodeProtocol {

    public HotpProtocol(CredentialPersistencePort persistencePort, SecretCipherPort cipher,
                        HmacSettings settings, OtpSettings otpSettings, Clock clock) {
        super(persistencePort, cipher, settings, otpSettings, clock);
    }

    @Override
    public OtpProtocol protocol() {
        return OtpProtocol.HOTP;
    }

    @Override
    public boolean expires() {
        return false;
    }

    @Override
    protected HmacType type() {
        return HmacType.HOTP;
    }

    @Override
    protected void ensurePeriodSupported(int periodSeconds) {
        // HOTP no tiene ventana de tiempo.
    }

    @Override
    protected IssuedCode codeFor(HmacCredential credential, byte[] secret, Instant now, int periodSeconds,
                                 int digits) {
        long counter = credential.getIssuedCounter() - 1;
        return new IssuedCode(HmacOtpAlgorithm.hotp(secret, counter, digits), null, counter, null);
    }

    @Override
    protected CodeMatch match(HmacCredential credential, byte[] secret, String code, Instant now) {
        return HotpVerifier.verify(secret, code, credential.getCounter(), pendingWindow(credential),
                settings.hotpLookAhead(), credential.getDigits());
    }

    @Override
    protected boolean claim(HmacCredential credential, long matched) {
        return persistence().claimCounter(credential.getId(), credential.getCounter(), matched + 1);
    }

    @Override
    protected VerifiedCode verifiedFor(long matched) {
        return new VerifiedCode(matched, null);
    }

    private long pendingWindow(HmacCredential credential) {
        return Math.min(credential.pendingCodes(), settings.hotpLookAhead()) - 1;
    }
}
