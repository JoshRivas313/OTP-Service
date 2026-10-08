package com.otpservice.otp.adapter.in.http;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.UUID;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("dev")
@SpringBootTest(properties = {
        "otp.demo-mode=true",
        "otp.rate-limit-per-destination=0",
        "otp.rate-limit-per-ip=0",
        "otp.verify-rate-limit-per-destination=0",
        "otp.verify-rate-limit-per-ip=0"
})
@AutoConfigureMockMvc
class ChannelProtocolsHttpTest {

    @Autowired
    private MockMvc mvc;

    private ResultActions post(String url, String body) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post(url)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String email() {
        return "canal-" + UUID.randomUUID().toString().substring(0, 8) + "@gmail.com";
    }

    @ParameterizedTest(name = "{0} por correo")
    @ValueSource(strings = {"OTP", "HOTP", "TOTP"})
    void enviarYVerificarPorCorreo(String protocol) throws Exception {
        String email = email();
        String sent = post("/api/email/otps", "{\"email\":\"" + email + "\",\"type\":\"" + protocol + "\",\"durationSeconds\":30}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value(protocol))
                .andReturn().getResponse().getContentAsString();
        String code = JsonPath.read(sent, "$.demoCode");

        post("/api/email/otps/verify", "{\"email\":\"" + email + "\",\"type\":\"" + protocol + "\",\"code\":\"" + code + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value(protocol));
    }

    @Test
    void hotpDevuelveElContadorYNoTieneVencimiento() throws Exception {
        post("/api/email/otps", "{\"email\":\"" + email() + "\",\"type\":\"HOTP\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.counter").value(0))
                .andExpect(jsonPath("$.expiresInSeconds").doesNotExist());
    }

    @Test
    void totpDevuelveLaVentana() throws Exception {
        post("/api/email/otps", "{\"email\":\"" + email() + "\",\"type\":\"TOTP\",\"durationSeconds\":30}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.timeStep").isNumber())
                .andExpect(jsonPath("$.counter").doesNotExist());
    }

    @Test
    void sinTypeSigueSiendoOtpAleatorio() throws Exception {
        String email = email();
        String sent = post("/api/email/otps", "{\"email\":\"" + email + "\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("OTP"))
                .andReturn().getResponse().getContentAsString();
        String code = JsonPath.read(sent, "$.demoCode");

        post("/api/email/otps/verify", "{\"email\":\"" + email + "\",\"code\":\"" + code + "\"}")
                .andExpect(status().isOk());
    }

    @Test
    void hotpConCuatroDigitosEsUnaPeticionInvalida() throws Exception {
        post("/api/email/otps", "{\"email\":\"" + email() + "\",\"type\":\"HOTP\",\"digits\":4}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OTP_INVALID_REQUEST"));
    }

    @Test
    void elPropositoViajaEnLaApiYSeExigeAlVerificar() throws Exception {
        String email = email();
        String sent = post("/api/email/otps", "{\"email\":\"" + email + "\",\"type\":\"OTP\",\"purpose\":\"LOGIN\"}")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String code = JsonPath.read(sent, "$.demoCode");

        post("/api/email/otps/verify", "{\"email\":\"" + email + "\",\"purpose\":\"PAYMENT_CONFIRMATION\",\"code\":\"" + code + "\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("OTP_NOT_FOUND"));
        post("/api/email/otps/verify", "{\"email\":\"" + email + "\",\"purpose\":\"login\",\"code\":\"" + code + "\"}")
                .andExpect(status().isOk());
    }

    @Test
    void unPropositoDesconocidoEsUnaPeticionInvalida() throws Exception {
        post("/api/email/otps", "{\"email\":\"" + email() + "\",\"purpose\":\"TRANSFERIR_TODO\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // La interfaz explica la tolerancia y los limites con estos mismos valores.
    @Test
    void laPoliticaExpuestaEsLaQueAplicaLaVerificacion() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/otp-policy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totpToleranceSteps").value(1))
                .andExpect(jsonPath("$.hotpLookAhead").value(10))
                .andExpect(jsonPath("$.maxAttempts").value(3))
                .andExpect(jsonPath("$.lockSeconds").value(600))
                .andExpect(jsonPath("$.customMessageEnabled").value(true));
    }
}
