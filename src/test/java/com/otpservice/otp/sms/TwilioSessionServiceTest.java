package com.otpservice.otp.sms;

import com.otpservice.otp.dto.valueobject.TwilioCredentials;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;

class TwilioSessionServiceTest {

    private static final TwilioCredentials CREDENTIALS = new TwilioCredentials(
            "AC" + "a".repeat(32), "b".repeat(32), "VA" + "c".repeat(32));

    private final TwilioSessionService service = new TwilioSessionService();

    @Test
    void noHayCredencialAntesDeConectar() {
        HttpSession session = new MockHttpSession();
        assertThat(service.isConnected(session)).isFalse();
        assertThat(service.get(session)).isEmpty();
    }

    @Test
    void connectGuardaLaCredencialEnLaSesion() {
        HttpSession session = new MockHttpSession();
        service.connect(session, CREDENTIALS);
        assertThat(service.isConnected(session)).isTrue();
        assertThat(service.get(session)).contains(CREDENTIALS);
    }

    @Test
    void disconnectBorraLaCredencial() {
        HttpSession session = new MockHttpSession();
        service.connect(session, CREDENTIALS);
        service.disconnect(session);
        assertThat(service.isConnected(session)).isFalse();
    }

    @Test
    void dosSesionesNoComparteCredenciales() {
        HttpSession sessionA = new MockHttpSession();
        HttpSession sessionB = new MockHttpSession();
        service.connect(sessionA, CREDENTIALS);
        assertThat(service.isConnected(sessionB)).isFalse();
    }
}
