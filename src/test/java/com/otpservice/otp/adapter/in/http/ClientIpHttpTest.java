package com.otpservice.otp.adapter.in.http;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Con Tomcat real: la IP que ven los limitadores es la que fija RemoteIpValve, no la que el cliente escribe
// en X-Forwarded-For. Cada caso levanta el servidor con otra configuracion de proxies.
class ClientIpHttpTest {

    private static final String LIMITS = "otp.verify-rate-limit-per-ip=5";

    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    static int verify(int port, String forwardedFor) throws Exception {
        String email = "ip-" + UUID.randomUUID().toString().substring(0, 8) + "@gmail.com";
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/email/otps/verify"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"" + email + "\",\"code\":\"123456\"}"));
        if (forwardedFor != null) {
            request.header("X-Forwarded-For", forwardedFor);
        }
        return CLIENT.send(request.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    // Conexion directa de un cliente que no es un proxy de confianza: X-Forwarded-For se ignora por completo.
    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
            "otp.demo-mode=true", LIMITS, "otp.verify-rate-limit-per-destination=0",
            "server.tomcat.remoteip.internal-proxies=10\\.255\\.255\\.254"
    })
    class SinProxyDeConfianza {

        @LocalServerPort
        int port;

        @Test
        void cambiarXForwardedForNoCreaIdentidadesNuevas() throws Exception {
            for (int i = 0; i < 5; i++) {
                assertThat(verify(port, "198.18.0." + i)).isEqualTo(404);
            }
            for (int i = 5; i < 20; i++) {
                assertThat(verify(port, "198.18.0." + i)).as("peticion %d", i).isEqualTo(429);
            }
        }
    }

    // Detras de un proxy de confianza (como el de Render): el proxy agrega la IP real al final y esa es la que cuenta.
    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
            "otp.demo-mode=true", LIMITS, "otp.verify-rate-limit-per-destination=0",
            "server.tomcat.remoteip.internal-proxies=127\\.0\\.0\\.1|0:0:0:0:0:0:0:1"
    })
    class DetrasDeUnProxyDeConfianza {

        @LocalServerPort
        int port;

        @Test
        void loQueElClienteEscribeALaIzquierdaSeIgnora() throws Exception {
            for (int i = 0; i < 5; i++) {
                assertThat(verify(port, "198.18.1." + i + ", 203.0.113.7")).isEqualTo(404);
            }
            for (int i = 5; i < 20; i++) {
                assertThat(verify(port, "198.18.1." + i + ", 203.0.113.7")).as("peticion %d", i).isEqualTo(429);
            }
        }

        @Test
        void dosClientesRealesTienenLimitesSeparados() throws Exception {
            for (int i = 0; i < 5; i++) {
                verify(port, "203.0.113.20");
            }
            assertThat(verify(port, "203.0.113.20")).isEqualTo(429);
            assertThat(verify(port, "203.0.113.21")).isEqualTo(404);
        }
    }

    // Control: con la estrategia anterior (framework) la IP la elegia el cliente y el limite no frenaba nada.
    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
            "otp.demo-mode=true", LIMITS, "otp.verify-rate-limit-per-destination=0",
            "server.forward-headers-strategy=framework"
    })
    class ConLaEstrategiaAnterior {

        @LocalServerPort
        int port;

        @Test
        void unaCabeceraFalsaPorPeticionEsquivabaElLimite() throws Exception {
            for (int i = 0; i < 20; i++) {
                assertThat(verify(port, "198.18.2." + i)).as("peticion %d", i).isEqualTo(404);
            }
        }
    }
}
