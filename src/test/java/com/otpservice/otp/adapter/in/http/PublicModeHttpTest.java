package com.otpservice.otp.adapter.in.http;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.otpservice.otp.adapter.out.email.ConsoleEmailSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Como un servidor publico: sin modo demo y sin mensaje personalizado. El texto lo decide el servidor.
@ActiveProfiles("dev")
@SpringBootTest(properties = {
        "otp.demo-mode=false",
        "otp.custom-message-enabled=false",
        "otp.rate-limit-per-destination=0",
        "otp.rate-limit-per-ip=0"
})
@AutoConfigureMockMvc
class PublicModeHttpTest {

    @Autowired
    private MockMvc mvc;

    private ListAppender<ILoggingEvent> sent;
    private Logger logger;

    @BeforeEach
    void captureTheEmail() {
        logger = (Logger) LoggerFactory.getLogger(ConsoleEmailSender.class);
        sent = new ListAppender<>();
        sent.start();
        logger.addAppender(sent);
    }

    @AfterEach
    void release() {
        logger.detachAppender(sent);
    }

    private ResultActions post(String body) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post("/api/email/otps")
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void elMensajePersonalizadoSeRechazaConUnErrorClaro() throws Exception {
        post("{\"email\":\"victima@ejemplo.com\",\"message\":\"Entra a http://sitio-falso.test {code}\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OTP_INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("El mensaje personalizado no está habilitado en este servidor"));

        assertThat(sent.list).isEmpty();
    }

    @Test
    void sinMensajeElServidorEscribeElTextoSegunElProposito() throws Exception {
        post("{\"email\":\"ana@ejemplo.com\",\"purpose\":\"PAYMENT_CONFIRMATION\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.demoCode").doesNotExist());

        assertThat(sent.list).singleElement().satisfies(event ->
                assertThat(event.getFormattedMessage())
                        .contains("Tu código para confirmar tu pago es ")
                        .contains("Vence en 30 segundos."));
    }

    @Test
    void laPoliticaAvisaALaInterfazQueNoHayMensajePersonalizado() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/otp-policy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customMessageEnabled").value(false));
    }
}
