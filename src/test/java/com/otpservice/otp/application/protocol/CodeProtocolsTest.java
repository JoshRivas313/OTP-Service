package com.otpservice.otp.application.protocol;

import com.otpservice.otp.domain.valueobject.Destination;
import com.otpservice.otp.domain.valueobject.OtpProtocol;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CodeProtocolsTest {

    private static CodeProtocol protocol(OtpProtocol kind) {
        return new CodeProtocol() {
            @Override
            public OtpProtocol protocol() {
                return kind;
            }

            @Override
            public boolean expires() {
                return true;
            }

            @Override
            public IssuedCode issue(Destination destination, int digits, int durationSeconds) {
                return new IssuedCode("123456", null, null, null);
            }

            @Override
            public VerifiedCode verify(Destination destination, String code) {
                return VerifiedCode.random();
            }
        };
    }

    @Test
    void returnsTheImplementationRegisteredForEachProtocol() {
        CodeProtocol otp = protocol(OtpProtocol.OTP);
        CodeProtocol totp = protocol(OtpProtocol.TOTP);
        CodeProtocols protocols = new CodeProtocols(List.of(otp, totp));

        assertThat(protocols.get(OtpProtocol.OTP)).isSameAs(otp);
        assertThat(protocols.get(OtpProtocol.TOTP)).isSameAs(totp);
    }

    @Test
    void failsClearlyWhenAProtocolHasNoImplementation() {
        CodeProtocols protocols = new CodeProtocols(List.of(protocol(OtpProtocol.OTP)));

        assertThatThrownBy(() -> protocols.get(OtpProtocol.HOTP))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HOTP");
    }
}
