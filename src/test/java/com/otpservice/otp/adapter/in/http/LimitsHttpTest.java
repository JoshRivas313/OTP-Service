package com.otpservice.otp.adapter.in.http;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Limite de conexiones de Twilio por IP y tope global de envios por dia, con valores bajos para verlos caer.
@ActiveProfiles("dev")
@SpringBootTest(properties = {
        "otp.demo-mode=true",
        "otp.rate-limit-per-destination=0",
        "otp.rate-limit-per-ip=0",
        "otp.connect-rate-limit-per-ip=2",
        "otp.daily-send-limit=2"
})
@AutoConfigureMockMvc
class LimitsHttpTest {

    @Autowired
    private MockMvc mvc;

    private ResultActions post(String url, String body) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post(url).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    // Credenciales con formato invalido: se rechazan antes de llamar a Twilio, pero cuentan para el limite.
    @Test
    void lasConexionesDeTwilioTienenLimitePorIp() throws Exception {
        String junk = "{\"accountSid\":\"x\",\"authToken\":\"x\",\"verifyServiceSid\":\"x\",\"phoneNumber\":\"x\"}";

        post("/api/twilio/connect", junk).andExpect(status().isUnauthorized());
        post("/api/twilio/connect", junk).andExpect(status().isUnauthorized());
        post("/api/twilio/connect", junk)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.message").value("Demasiados intentos de conexión. Prueba de nuevo en unos minutos"));
    }

    @Test
    void alAgotarseLaCuotaDiariaElEnvioResponde503ConUnMensajeAmable() throws Exception {
        post("/api/email/otps", "{\"email\":\"cuota1@gmail.com\"}").andExpect(status().isCreated());
        post("/api/email/otps", "{\"email\":\"cuota2@gmail.com\"}").andExpect(status().isCreated());

        post("/api/email/otps", "{\"email\":\"cuota3@gmail.com\"}")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("DAILY_QUOTA_EXCEEDED"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("agotó sus envíos de hoy")));
    }
}
