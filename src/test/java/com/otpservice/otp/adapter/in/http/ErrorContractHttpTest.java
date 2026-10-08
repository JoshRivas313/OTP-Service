package com.otpservice.otp.adapter.in.http;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Todos los errores, tambien los del framework, salen como {success, code, message} y sin detalles internos.
@ActiveProfiles("dev")
@SpringBootTest(properties = {
        "otp.demo-mode=true",
        "otp.rate-limit-per-destination=0",
        "otp.rate-limit-per-ip=0",
        "otp.verify-rate-limit-per-destination=0",
        "otp.verify-rate-limit-per-ip=0"
})
@AutoConfigureMockMvc
@Import(ErrorContractHttpTest.Boom.class)
class ErrorContractHttpTest {

    // Una ruta que falla de forma inesperada, solo para este test.
    @RestController
    static class Boom {
        @GetMapping("/test-boom")
        String boom() {
            throw new IllegalStateException("SECRETO-INTERNO jdbc:mongodb://usuario:clave@host");
        }
    }

    @Autowired
    private MockMvc mvc;

    private ResultActions post(String url, String contentType, String body) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post(url).contentType(contentType).content(body));
    }

    private static ResultActions expectEnvelope(ResultActions result, int status, String code) throws Exception {
        return result.andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.timestamp").doesNotExist())
                .andExpect(jsonPath("$.path").doesNotExist());
    }

    @Test
    void unaRutaInexistenteResponde404ConElSobreDeError() throws Exception {
        expectEnvelope(mvc.perform(MockMvcRequestBuilders.get("/no-existe")), 404, "RESOURCE_NOT_FOUND");
    }

    @Test
    void unMetodoNoPermitidoResponde405ConElSobreYLaCabeceraAllow() throws Exception {
        expectEnvelope(mvc.perform(MockMvcRequestBuilders.get("/api/email/otps")), 405, "METHOD_NOT_ALLOWED")
                .andExpect(header().exists("Allow"));
    }

    @Test
    void unFormatoNoSoportadoResponde415ConElSobre() throws Exception {
        expectEnvelope(post("/api/email/otps", "text/plain", "hola"), 415, "UNSUPPORTED_MEDIA_TYPE");
    }

    @Test
    void unJsonMalFormadoResponde400ConElSobre() throws Exception {
        expectEnvelope(post("/api/email/otps", "application/json", "{\"email\":"), 400, "VALIDATION_ERROR");
    }

    @Test
    void unValorFueraDeCatalogoResponde400ConElSobre() throws Exception {
        expectEnvelope(post("/api/email/otps", "application/json",
                "{\"email\":\"a@b.co\",\"purpose\":\"TRANSFERIR_TODO\"}"), 400, "VALIDATION_ERROR");
    }

    @Test
    void unFalloInesperadoResponde500SinFiltrarSuMensaje() throws Exception {
        expectEnvelope(mvc.perform(MockMvcRequestBuilders.get("/test-boom")), 500, "INTERNAL_ERROR")
                .andExpect(content().string(not(containsString("SECRETO-INTERNO"))))
                .andExpect(content().string(not(containsString("mongodb"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))));
    }

    // Un codigo con formato invalido es un codigo incorrecto (401) en los tres endpoints de verificar, no un 400.
    @Test
    void unCodigoDeFormatoInvalidoEsUnCodigoIncorrectoEnElEndpointDeCelular() throws Exception {
        expectEnvelope(post("/otps/verify", "application/json", "{\"cellphone\":\"987654321\",\"code\":\"123\"}"),
                401, "OTP_INVALID");
    }

    @Test
    void unCodigoDeFormatoInvalidoEsUnCodigoIncorrectoEnElEndpointDeCorreo() throws Exception {
        expectEnvelope(post("/api/email/otps/verify", "application/json", "{\"email\":\"a@b.co\",\"code\":\"123\"}"),
                401, "OTP_INVALID");
    }
}
