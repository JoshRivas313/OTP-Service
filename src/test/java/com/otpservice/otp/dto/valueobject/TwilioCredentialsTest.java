package com.otpservice.otp.dto.valueobject;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TwilioCredentialsTest {

    private static final String ACCOUNT_SID = "AC" + "a".repeat(32);
    private static final String AUTH_TOKEN = "b".repeat(32);
    private static final String VERIFY_SERVICE_SID = "VA" + "c".repeat(32);
    private static final String PHONE_NUMBER = "+15017122661";

    @Test
    void aceptaCredencialesConFormatoValido() {
        TwilioCredentials credentials = new TwilioCredentials(ACCOUNT_SID, AUTH_TOKEN, VERIFY_SERVICE_SID, PHONE_NUMBER);
        assertThat(credentials.getAccountSid()).isEqualTo(ACCOUNT_SID);
        assertThat(credentials.getVerifyServiceSid()).isEqualTo(VERIFY_SERVICE_SID);
        assertThat(credentials.getPhoneNumber()).isEqualTo(PHONE_NUMBER);
    }

    @Test
    void rechazaAccountSidSinPrefijoAC() {
        assertThatThrownBy(() -> new TwilioCredentials("XX" + "a".repeat(32), AUTH_TOKEN, VERIFY_SERVICE_SID, PHONE_NUMBER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rechazaAuthTokenDeLargoInvalido() {
        assertThatThrownBy(() -> new TwilioCredentials(ACCOUNT_SID, "corto", VERIFY_SERVICE_SID, PHONE_NUMBER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rechazaVerifyServiceSidSinPrefijoVA() {
        assertThatThrownBy(() -> new TwilioCredentials(ACCOUNT_SID, AUTH_TOKEN, "XX" + "c".repeat(32), PHONE_NUMBER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rechazaNumeroSinFormatoE164() {
        assertThatThrownBy(() -> new TwilioCredentials(ACCOUNT_SID, AUTH_TOKEN, VERIFY_SERVICE_SID, "987654321"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void masked_nuncaExponeElAuthToken() {
        TwilioCredentials credentials = new TwilioCredentials(ACCOUNT_SID, AUTH_TOKEN, VERIFY_SERVICE_SID, PHONE_NUMBER);
        assertThat(credentials.masked()).doesNotContain(AUTH_TOKEN);
        assertThat(credentials.toString()).doesNotContain(AUTH_TOKEN);
    }
}
