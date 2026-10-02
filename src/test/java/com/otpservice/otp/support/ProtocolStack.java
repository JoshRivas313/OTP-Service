package com.otpservice.otp.support;

import com.otpservice.otp.adapter.config.HmacProperties;
import com.otpservice.otp.adapter.config.OtpProperties;
import com.otpservice.otp.adapter.out.persistence.memory.InMemoryCredentialAdapter;
import com.otpservice.otp.adapter.out.persistence.memory.InMemoryOtpPersistenceAdapter;
import com.otpservice.otp.adapter.out.security.AesGcmSecretCipher;
import com.otpservice.otp.adapter.out.security.CodeHasher;
import com.otpservice.otp.application.config.HmacSettings;
import com.otpservice.otp.application.config.OtpSettings;
import com.otpservice.otp.application.dto.GenerateOtpResult;
import com.otpservice.otp.application.dto.VerifyOtpResult;
import com.otpservice.otp.application.port.in.GenerateOtpUseCase.GenerateOtpCommand;
import com.otpservice.otp.application.port.in.VerifyOtpUseCase.VerifyOtpCommand;
import com.otpservice.otp.application.protocol.CodeProtocols;
import com.otpservice.otp.application.protocol.HotpProtocol;
import com.otpservice.otp.application.protocol.RandomCodeProtocol;
import com.otpservice.otp.application.protocol.TotpProtocol;
import com.otpservice.otp.application.usecase.GenerateOtpUseCaseImpl;
import com.otpservice.otp.application.usecase.VerifyOtpUseCaseImpl;
import com.otpservice.otp.domain.model.HmacCredential;
import com.otpservice.otp.domain.valueobject.Destination;
import com.otpservice.otp.domain.valueobject.OtpCode;
import com.otpservice.otp.domain.valueobject.OtpProtocol;
import com.otpservice.otp.domain.valueobject.Purpose;

import java.time.Instant;
import java.util.List;

// Arma los tres protocolos con almacenamiento en memoria y un reloj controlable, sin levantar Spring.
public final class ProtocolStack {

    public static final int MAX_ATTEMPTS = 3;
    public static final int LOCK_SECONDS = 600;

    public final MutableClock clock = new MutableClock(Instant.parse("2026-10-01T14:00:10Z"));
    public final InMemoryOtpPersistenceAdapter otpStore;
    public final InMemoryCredentialAdapter credentials;
    public final AesGcmSecretCipher cipher;
    private final GenerateOtpUseCaseImpl generate;
    private final VerifyOtpUseCaseImpl verify;
    private String lastMessage;

    public ProtocolStack() {
        OtpProperties otp = properties();
        HmacSettings settings = new HmacSettings(1, 10);
        OtpSettings otpSettings = otp.settings();
        otpStore = new InMemoryOtpPersistenceAdapter(clock, otp);
        credentials = new InMemoryCredentialAdapter(otp);
        cipher = new AesGcmSecretCipher(new HmacProperties(1, 10, HmacProperties.INSECURE_DEV_KEY));
        CodeProtocols protocols = new CodeProtocols(List.of(
                new RandomCodeProtocol(otpStore, new CodeHasher(otp), otpSettings, clock),
                new HotpProtocol(credentials, cipher, settings, otpSettings, clock),
                new TotpProtocol(credentials, cipher, settings, otpSettings, clock)));
        generate = new GenerateOtpUseCaseImpl(protocols, otpSettings);
        verify = new VerifyOtpUseCaseImpl(protocols);
    }

    public static OtpProperties properties() {
        return new OtpProperties(6, 30, MAX_ATTEMPTS, 86400,
                "Tu código de verificación es %s. Vence en %d segundos.", "secreto", true, 10000,
                0, 0, 600, LOCK_SECONDS, 0, 0, true);
    }

    public GenerateOtpResult send(Destination to, OtpProtocol protocol, Purpose purpose, Integer digits,
                                  Integer duration) {
        return generate.generate(new GenerateOtpCommand(to, protocol, purpose, digits, duration, null),
                (d, m) -> lastMessage = m);
    }

    public GenerateOtpResult send(Destination to, OtpProtocol protocol, Purpose purpose, Integer duration) {
        return send(to, protocol, purpose, 6, duration);
    }

    public VerifyOtpResult check(Destination to, OtpProtocol protocol, Purpose purpose, String code) {
        return verify.verify(new VerifyOtpCommand(to, protocol, purpose, new OtpCode(code)));
    }

    // El secreto en claro de una credencial, para recalcular sus codigos fuera del servicio.
    public byte[] secretOf(HmacCredential credential) {
        return cipher.decrypt(credential.getSecret(), credential.secretContext()).bytes();
    }

    public String lastMessage() {
        return lastMessage;
    }

    public static String wrong(String right) {
        return right.equals("000000") ? "000001" : "000000";
    }
}
