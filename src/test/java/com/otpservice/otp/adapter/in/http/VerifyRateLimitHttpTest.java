package com.otpservice.otp.adapter.in.http;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// El limite de verificaciones es propio: no depende del de envios (apagado aqui) ni de los intentos por codigo.
@ActiveProfiles("dev")
@SpringBootTest(properties = {
        "otp.demo-mode=true",
        "otp.rate-limit-per-destination=0",
        "otp.rate-limit-per-ip=0",
        "otp.verify-rate-limit-per-destination=3",
        "otp.verify-rate-limit-per-ip=5"
})
@AutoConfigureMockMvc
class VerifyRateLimitHttpTest {

    private static final AtomicInteger NEXT_IP = new AtomicInteger(1);

    @Autowired
    private MockMvc mvc;

    private ResultActions post(String url, String body, String ip) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post(url)
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions verify(String email, String code, String ip) throws Exception {
        return post("/api/email/otps/verify", "{\"email\":\"" + email + "\",\"code\":\"" + code + "\"}", ip);
    }

    private static String email() {
        return "limite-" + UUID.randomUUID().toString().substring(0, 8) + "@gmail.com";
    }

    private static String ip() {
        int n = NEXT_IP.getAndIncrement();
        return "203.0." + (n / 250) + "." + (n % 250 + 1);
    }

    @Test
    void porDestinoResponde429AunqueCambieLaIp() throws Exception {
        String email = email();
        String code = JsonPath.read(post("/api/email/otps", "{\"email\":\"" + email + "\"}", ip())
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.demoCode");

        // Tres fallos agotan los intentos del codigo; pedir otro da intentos nuevos, pero no verificaciones nuevas.
        verify(email, "000000".equals(code) ? "111111" : "000000", ip()).andExpect(status().isUnauthorized());
        verify(email, "000000".equals(code) ? "111111" : "000000", ip()).andExpect(status().isUnauthorized());
        String again = JsonPath.read(post("/api/email/otps", "{\"email\":\"" + email + "\"}", ip())
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.demoCode");
        verify(email, "000000".equals(again) ? "111111" : "000000", ip()).andExpect(status().isUnauthorized());

        verify(email, again, ip())
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.message").value("Demasiados intentos de verificación. Prueba de nuevo en unos minutos"));
    }

    @Test
    void porIpResponde429AunqueCambieElDestino() throws Exception {
        String ip = ip();
        for (int i = 0; i < 5; i++) {
            verify(email(), "123456", ip).andExpect(status().isNotFound());
        }

        verify(email(), "123456", ip)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"));
    }

    @Test
    void elLimiteDeVerificacionNoFrenaLosEnvios() throws Exception {
        String ip = ip();
        for (int i = 0; i < 5; i++) {
            verify(email(), "123456", ip);
        }
        verify(email(), "123456", ip).andExpect(status().isTooManyRequests());

        post("/api/email/otps", "{\"email\":\"" + email() + "\"}", ip).andExpect(status().isCreated());
    }

    @Test
    void tambienAplicaALaApiGenerica() throws Exception {
        String ip = ip();
        for (int i = 0; i < 5; i++) {
            post("/otps/verify", "{\"cellphone\":\"+51987654" + String.format("%03d", i) + "\",\"code\":\"123456\"}", ip);
        }

        post("/otps/verify", "{\"cellphone\":\"+51987654999\",\"code\":\"123456\"}", ip)
                .andExpect(status().isTooManyRequests());
    }
}
