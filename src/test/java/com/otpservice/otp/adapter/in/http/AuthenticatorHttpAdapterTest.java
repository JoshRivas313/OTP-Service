package com.otpservice.otp.adapter.in.http;

import com.jayway.jsonpath.JsonPath;
import com.otpservice.otp.domain.service.HmacOtpAlgorithm;
import com.otpservice.otp.support.TestBase32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;

@SpringBootTest(properties = {
        "otp.demo-mode=true",
        "otp.rate-limit-per-destination=0",
        "otp.rate-limit-per-ip=0"
})
@AutoConfigureMockMvc
class AuthenticatorHttpAdapterTest {

    @Autowired
    private MockMvc mvc;

    private MockHttpSession session;
    private String email;

    @BeforeEach
    void nuevoVisitante() {
        session = new MockHttpSession();
        email = "visitante-" + UUID.randomUUID().toString().substring(0, 8) + "@gmail.com";
    }

    private ResultActions postJson(String url, String body) throws Exception {
        return mvc.perform(post(url).session(session).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private void verificarCorreo() throws Exception {
        String sent = postJson("/api/email/otps", "{\"email\":\"" + email + "\"}")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String code = JsonPath.read(sent, "$.demoCode");
        postJson("/api/email/otps/verify", "{\"email\":\"" + email + "\",\"code\":\"" + code + "\"}")
                .andExpect(status().isOk());
    }

    private byte[] registrarTotp() throws Exception {
        String body = postJson("/api/authenticator/enrollments", "{\"email\":\"" + email + "\",\"type\":\"TOTP\"}")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return TestBase32.decode(JsonPath.read(body, "$.secretBase32"));
    }

    private String codeBody(String type, String code) {
        return "{\"email\":\"" + email + "\",\"type\":\"" + type + "\",\"code\":\"" + code + "\"}";
    }

    @Test
    void sinVerificarElCorreoNoSePuedeRegistrarUnaApp() throws Exception {
        postJson("/api/authenticator/enrollments", "{\"email\":\"" + email + "\",\"type\":\"TOTP\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
    }

    @Test
    void elCorreoVerificadoSoloHabilitaEseCorreo() throws Exception {
        verificarCorreo();

        postJson("/api/authenticator/enrollments", "{\"email\":\"otro@gmail.com\",\"type\":\"TOTP\"}")
                .andExpect(status().isForbidden());
    }

    @Test
    void registroDevuelveQrYSecretoSinCache() throws Exception {
        verificarCorreo();

        postJson("/api/authenticator/enrollments", "{\"email\":\"" + email + "\",\"type\":\"TOTP\",\"digits\":6,\"periodSeconds\":30}")
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.qrSvg").value(startsWith("<svg")))
                .andExpect(jsonPath("$.otpauthUri").value(startsWith("otpauth://totp/")))
                .andExpect(jsonPath("$.periodSeconds").value(30));
    }

    @Test
    void flujoCompletoTotp() throws Exception {
        verificarCorreo();
        byte[] secret = registrarTotp();
        String now = HmacOtpAlgorithm.totp(secret, Instant.now(), 30, 6);

        postJson("/api/authenticator/verify", codeBody("TOTP", now))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ENROLLMENT_NOT_CONFIRMED"));

        postJson("/api/authenticator/enrollments/confirm", codeBody("TOTP", now))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timeStep").isNumber());

        postJson("/api/authenticator/verify", codeBody("TOTP", now))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OTP_ALREADY_USED"));

        String next = HmacOtpAlgorithm.totp(secret, Instant.now().plusSeconds(30), 30, 6);
        postJson("/api/authenticator/verify", codeBody("TOTP", next))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void flujoCompletoHotp() throws Exception {
        verificarCorreo();
        String body = postJson("/api/authenticator/enrollments", "{\"email\":\"" + email + "\",\"type\":\"HOTP\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.counter").value(0))
                .andReturn().getResponse().getContentAsString();
        byte[] secret = TestBase32.decode(JsonPath.read(body, "$.secretBase32"));

        postJson("/api/authenticator/enrollments/confirm", codeBody("HOTP", HmacOtpAlgorithm.hotp(secret, 0, 6)))
                .andExpect(status().isOk());
        postJson("/api/authenticator/verify", codeBody("HOTP", HmacOtpAlgorithm.hotp(secret, 3, 6)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.counter").value(3));
    }

    @Test
    void codigoIncorrectoIndicaElIntento() throws Exception {
        verificarCorreo();
        byte[] secret = registrarTotp();
        String right = HmacOtpAlgorithm.totp(secret, Instant.now(), 30, 6);
        String wrong = right.equals("000000") ? "000001" : "000000";

        postJson("/api/authenticator/enrollments/confirm", codeBody("TOTP", wrong))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("OTP_INVALID"))
                .andExpect(jsonPath("$.message").value(containsString("intento 1 de 5")));
    }

    @Test
    void eliminarExigeCorreoVerificadoYDespuesNoHayRegistro() throws Exception {
        verificarCorreo();
        registrarTotp();

        mvc.perform(delete("/api/authenticator/enrollments").param("email", email).param("type", "TOTP")
                        .session(new MockHttpSession()))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/authenticator/enrollments").param("email", email).param("type", "TOTP")
                        .session(session))
                .andExpect(status().isNoContent());
        postJson("/api/authenticator/verify", codeBody("TOTP", "123456"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ENROLLMENT_NOT_FOUND"));
    }

    @Test
    void codigoConFormatoInvalidoSeRechazaAntesDeLlegarAlCasoDeUso() throws Exception {
        postJson("/api/authenticator/verify", codeBody("TOTP", "12ab"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void healthIndicaQueElAlmacenamientoEsEnMemoria() throws Exception {
        mvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storage").value("memory"));
    }
}
