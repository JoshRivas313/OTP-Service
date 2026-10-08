package com.otpservice.otp.adapter.out.sms;

import com.otpservice.otp.adapter.config.SmsProperties;
import com.otpservice.otp.adapter.config.SmsProvider;
import com.otpservice.otp.adapter.exception.SmsDeliveryFailedException;
import com.otpservice.otp.adapter.out.StubHttpServer;
import com.otpservice.otp.domain.valueobject.Cellphone;
import com.otpservice.otp.support.LogCapture;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// El cliente de Infobip contra un servidor HTTP de mentira.
class InfobipSmsSenderTest {

    private static final String MESSAGE = "Tu código para iniciar sesión es 482913. Vence en 30 segundos.";

    private static InfobipSmsSender infobip(StubHttpServer server) {
        SmsProperties properties = new SmsProperties(SmsProvider.INFOBIP, new SmsProperties.Twilio("", "", ""),
                new SmsProperties.Infobip(server.baseUrl(), "clave-infobip", "UnSoloUso"));
        InfobipSmsSender sender = new InfobipSmsSender(properties);
        sender.initialize();
        return sender;
    }

    @Test
    void recibeElSmsConLaClaveYElNumeroSinElMas() throws Exception {
        try (StubHttpServer server = new StubHttpServer().respond(200, "{}")) {
            infobip(server).send(new Cellphone("987654321"), MESSAGE);

            assertThat(server.received()).singleElement().satisfies(request -> {
                assertThat(request.path()).isEqualTo("/sms/3/messages");
                assertThat(request.authorization()).isEqualTo("App clave-infobip");
                assertThat(request.body()).contains("\"to\":\"51987654321\"").contains("UnSoloUso").contains("482913");
            });
        }
    }

    @Test
    void siInfobipRechazaElEnvioSeLanzaUnErrorDeEntregaSinFiltrarElNumero() throws Exception {
        try (StubHttpServer server = new StubHttpServer().respond(500, "{\"error\":\"boom\"}");
             LogCapture logs = new LogCapture(InfobipSmsSender.class)) {
            InfobipSmsSender sender = infobip(server);

            assertThatThrownBy(() -> sender.send(new Cellphone("987654321"), MESSAGE))
                    .isInstanceOf(SmsDeliveryFailedException.class);

            assertThat(logs.text()).contains("*********321").doesNotContain("987654321");
        }
    }

    @Test
    void sinDatosNoArranca() {
        SmsProperties sinDatos = new SmsProperties(SmsProvider.INFOBIP, new SmsProperties.Twilio("", "", ""),
                new SmsProperties.Infobip("", "", ""));

        assertThatThrownBy(() -> new InfobipSmsSender(sinDatos).initialize())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("INFOBIP_API_KEY");
    }
}
