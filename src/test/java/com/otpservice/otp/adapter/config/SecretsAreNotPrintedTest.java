package com.otpservice.otp.adapter.config;

import com.otpservice.otp.adapter.out.sms.twilio.TwilioErrorMessages;
import com.otpservice.otp.support.TestOtpProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

// Un record imprime todos sus componentes: los que guardan secretos no deben aparecer en un toString ni en un log.
class SecretsAreNotPrintedTest {

    private static final String SECRET = "valor-secreto-que-no-debe-salir";

    @Test
    void laConfiguracionDeOtpYDeCifradoNoImprimeSusClaves() {
        assertThat(TestOtpProperties.otp().hashSecret(SECRET).build().toString()).doesNotContain(SECRET);
        assertThat(new HmacProperties(1, 10, SECRET).toString()).doesNotContain(SECRET);
    }

    @Test
    void laConfiguracionDeLosProveedoresNoImprimeSusClaves() {
        assertThat(new EmailProperties.Brevo(SECRET, "https://api.brevo.com").toString()).doesNotContain(SECRET);
        assertThat(new SmsProperties.Twilio("AC" + SECRET, SECRET, "+15017122661").toString()).doesNotContain(SECRET);
        assertThat(new SmsProperties.Infobip("https://x", SECRET, "Remitente").toString()).doesNotContain(SECRET);
        assertThat(new EmailProperties(EmailProvider.BREVO, "Un Solo Uso", "r@correo.com",
                new EmailProperties.Brevo(SECRET, "https://api.brevo.com")).toString()).doesNotContain(SECRET);
    }

    @ParameterizedTest(name = "codigo {0}")
    @CsvSource(delimiter = '|', value = {
            "21608|es de prueba",
            "21211|no reconoce ese número",
            "21614|no reconoce ese número",
            "21408|permiso para enviar SMS a Perú",
            "21606|no puede enviar SMS",
            "21610|pidió no recibir mensajes",
            "20003|rechazó las credenciales"
    })
    void cadaCodigoDeTwilioConocidoTieneUnaExplicacionParaElUsuario(int code, String fragment) {
        assertThat(TwilioErrorMessages.describe(code)).contains(fragment);
    }

    @Test
    void unCodigoDesconocidoOUnErrorSinCodigoNoRompeNiExplicaDeMas() {
        assertThat(TwilioErrorMessages.describe(99999)).contains("99999");
        assertThat(TwilioErrorMessages.describe(null)).isEqualTo("No se pudo enviar el SMS");
    }
}
