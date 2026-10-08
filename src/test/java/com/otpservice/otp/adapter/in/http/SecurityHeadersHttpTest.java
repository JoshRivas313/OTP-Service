package com.otpservice.otp.adapter.in.http;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("dev")
@SpringBootTest(properties = {
        "otp.demo-mode=true",
        "otp.rate-limit-per-destination=0",
        "otp.rate-limit-per-ip=0",
        "springdoc.api-docs.enabled=true"
})
@AutoConfigureMockMvc
class SecurityHeadersHttpTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void laPaginaYLaApiLlevanLasCabecerasDeSeguridad() throws Exception {
        for (String path : new String[]{"/", "/correo.html", "/health", "/api/otp-policy"}) {
            mvc.perform(MockMvcRequestBuilders.get(path))
                    .andExpect(status().isOk())
                    .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                    .andExpect(header().string("X-Frame-Options", "DENY"))
                    .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                    .andExpect(header().exists("Permissions-Policy"));
        }
    }

    @Test
    void laPoliticaDeContenidoSoloPermiteScriptsDeEsteOrigenYNingunMarco() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/"))
                .andExpect(header().string("Content-Security-Policy", containsString("script-src 'self'")))
                .andExpect(header().string("Content-Security-Policy", containsString("frame-ancestors 'none'")))
                .andExpect(header().string("Content-Security-Policy", containsString("object-src 'none'")))
                .andExpect(header().string("Content-Security-Policy", containsString("connect-src 'self'")));
    }

    @Test
    void laPoliticaNoCubreLaDocumentacionDeSwagger() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Content-Type-Options"))
                .andExpect(header().doesNotExist("Content-Security-Policy"));
    }

    @Test
    void strictTransportSecuritySoloSeEnviaPorHttps() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/health"))
                .andExpect(header().doesNotExist("Strict-Transport-Security"));
        mvc.perform(MockMvcRequestBuilders.get("/health").secure(true))
                .andExpect(header().string("Strict-Transport-Security", containsString("max-age=31536000")));
    }

    @Test
    void unCuerpoDeMasDeDieciseisKilobytesSeRechazaConUn413ConElSobreDeError() throws Exception {
        String body = "{\"email\":\"a@b.co\",\"message\":\"" + "x".repeat(20_000) + "\"}";

        mvc.perform(MockMvcRequestBuilders.post("/api/email/otps").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void unCuerpoNormalSigueFuncionando() throws Exception {
        mvc.perform(MockMvcRequestBuilders.post("/api/email/otps").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"normal@gmail.com\"}"))
                .andExpect(status().isCreated());
    }
}
