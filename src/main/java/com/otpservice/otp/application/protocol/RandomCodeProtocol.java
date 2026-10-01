package com.otpservice.otp.application.protocol;

import com.otpservice.otp.application.config.OtpSettings;
import com.otpservice.otp.application.exception.OtpNotFoundException;
import com.otpservice.otp.application.port.out.CodeHasherPort;
import com.otpservice.otp.application.port.out.OtpPersistencePort;
import com.otpservice.otp.domain.exception.InvalidOtpException;
import com.otpservice.otp.domain.exception.OtpBlockedException;
import com.otpservice.otp.domain.model.Otp;
import com.otpservice.otp.domain.valueobject.Destination;
import com.otpservice.otp.domain.valueobject.OtpCode;
import com.otpservice.otp.domain.valueobject.OtpProtocol;
import com.otpservice.otp.domain.valueobject.ValidityWindow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

// OTP: un codigo al azar, del que el servidor guarda solo el hash.
@Component
@RequiredArgsConstructor
public class RandomCodeProtocol implements CodeProtocol {

    private final OtpPersistencePort persistencePort;
    private final CodeHasherPort codeHasher;
    private final OtpSettings settings;
    private final Clock clock;

    @Override
    public OtpProtocol protocol() {
        return OtpProtocol.OTP;
    }

    @Override
    public boolean expires() {
        return true;
    }

    @Override
    public IssuedCode issue(Destination destination, int digits, int durationSeconds) {
        persistencePort.invalidateActive(destination.getValue());

        Instant now = clock.instant();
        OtpCode code = OtpCode.generate(digits);
        ValidityWindow window = ValidityWindow.from(now, durationSeconds);
        String codeHash = codeHasher.hash(code.getValue());
        Instant purgeAt = window.getExpiresAt().plusSeconds(settings.retentionSeconds());

        persistencePort.save(Otp.issue(new Otp.IssueRequest(destination, codeHash, digits, window, purgeAt)));
        return new IssuedCode(code.getValue(), (long) durationSeconds, null, null);
    }

    @Override
    public VerifiedCode verify(Destination destination, String code) {
        Instant now = clock.instant();
        int maxAttempts = settings.maxAttempts();

        Otp otp = persistencePort.findLatestByDestination(destination.getValue())
                .orElseThrow(OtpNotFoundException::new);
        otp.ensureVerifiable(now, maxAttempts);

        String codeHash = codeHasher.hash(code);
        if (persistencePort.claimIfMatches(otp.getId(), codeHash, now, maxAttempts).isPresent()) {
            return VerifiedCode.random();
        }

        Otp updated = persistencePort.registerFailedAttempt(otp.getId())
                .orElseThrow(OtpNotFoundException::new);
        if (updated.isBlocked(maxAttempts)) {
            throw new OtpBlockedException();
        }
        throw InvalidOtpException.attempt(updated.getAttempts(), maxAttempts);
    }
}
