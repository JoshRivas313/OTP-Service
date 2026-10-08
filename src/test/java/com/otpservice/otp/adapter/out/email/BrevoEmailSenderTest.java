package com.otpservice.otp.adapter.out.email;

import com.otpservice.otp.adapter.config.EmailProperties;
import com.otpservice.otp.adapter.config.EmailProvider;
import com.otpservice.otp.adapter.exception.EmailDeliveryFailedException;
import com.otpservice.otp.adapter.out.StubHttpServer;
import com.otpservice.otp.domain.valueobject.EmailAddress;
import com.otpservice.otp.support.LogCapture;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// El cliente de Brevo contra un servidor HTTP de mentira: lo que envia y como reacciona cuando falla.
class BrevoEmailSenderTest {

    private static final String MESSAGE = "Tu código para iniciar sesión es 482913. Vence en 30 segundos.";

    private static BrevoEmailSender brevo(StubHttpServer server, String apiKey) {
        EmailProperties properties = new EmailProperties(EmailProvider.BREVO, "Un Solo Uso", "remitente@correo.com",
                new EmailProperties.Brevo(apiKey, server.baseUrl()));
        BrevoEmailSender sender = new BrevoEmailSender(properties);
        sender.initialize();
        return sender;
    }

    @Test
    void recibeElCorreoConLaClaveYElRemitenteConfigurados() throws Exception {
        try (StubHttpServer server = new StubHttpServer().respond(201, "{\"messageId\":\"x\"}")) {
            brevo(server, "clave-brevo").send(new EmailAddress("jose@gmail.com"), MESSAGE);

            assertThat(server.received()).singleElement().satisfies(request -> {
                assertThat(request.method()).isEqualTo("POST");
                assertThat(request.path()).isEqualTo("/v3/smtp/email");
                assertThat(request.apiKey()).isEqualTo("clave-brevo");
                assertThat(request.body())
                        .contains("\"email\":\"jose@gmail.com\"")
                        .contains("\"email\":\"remitente@correo.com\"")
                        .contains("Un Solo Uso")
                        .contains("482913");
            });
        }
    }

    @Test
    void siBrevoRechazaElEnvioSeLanzaUnErrorDeEntregaYElLogNoLlevaElCorreoCompleto() throws Exception {
        String echo = "{\"code\":\"invalid_parameter\",\"message\":\"el destinatario jose@gmail.com no es valido\"}";
        try (StubHttpServer server = new StubHttpServer().respond(400, echo);
             LogCapture logs = new LogCapture(BrevoEmailSender.class)) {
            BrevoEmailSender sender = brevo(server, "clave-brevo");

            assertThatThrownBy(() -> sender.send(new EmailAddress("jose@gmail.com"), MESSAGE))
                    .isInstanceOf(EmailDeliveryFailedException.class);

            assertThat(logs.text()).contains("j***@gmail.com").contains("estado=400")
                    .doesNotContain("jose@gmail.com").doesNotContain("clave-brevo");
        }
    }

    @Test
    void siBrevoNoRespondeSeLanzaUnErrorDeEntrega() throws Exception {
        StubHttpServer server = new StubHttpServer();
        BrevoEmailSender sender = brevo(server, "clave-brevo");
        server.close();

        assertThatThrownBy(() -> sender.send(new EmailAddress("jose@gmail.com"), MESSAGE))
                .isInstanceOf(EmailDeliveryFailedException.class);
    }

    @Test
    void sinClaveONiRemitenteNoArranca() throws Exception {
        try (StubHttpServer server = new StubHttpServer()) {
            EmailProperties sinClave = new EmailProperties(EmailProvider.BREVO, "Un Solo Uso", "r@correo.com",
                    new EmailProperties.Brevo("", server.baseUrl()));
            assertThatThrownBy(() -> new BrevoEmailSender(sinClave).initialize())
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("BREVO_API_KEY");

            EmailProperties sinRemitente = new EmailProperties(EmailProvider.BREVO, "Un Solo Uso", "",
                    new EmailProperties.Brevo("clave", server.baseUrl()));
            assertThatThrownBy(() -> new BrevoEmailSender(sinRemitente).initialize())
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("EMAIL_SENDER_ADDRESS");
        }
    }
}
