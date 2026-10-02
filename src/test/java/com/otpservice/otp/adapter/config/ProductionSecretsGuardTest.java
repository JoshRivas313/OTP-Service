package com.otpservice.otp.adapter.config;

import com.otpservice.otp.OtpServiceApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Arrays;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionSecretsGuardTest {

    private static final String OWN_KEY = "q2Zt0f1pY8m3vXc7Lr5sWb9nHk4Jd6Ae2Gu0Ti8Oy1M=";

    private static ProductionSecretsGuard guard(String hashSecret, String encryptionKey) {
        OtpProperties otp = new OtpProperties(6, 30, 3, 86400, "plantilla", hashSecret, false, 10000, 5, 20, 600,
                600, 10, 30, false);
        return new ProductionSecretsGuard(otp, new HmacProperties(1, 10, encryptionKey));
    }

    @Test
    void aceptaClavesPropias() {
        assertThatCode(() -> guard("un-secreto-propio-largo", OWN_KEY).checkSecrets()).doesNotThrowAnyException();
    }

    @Test
    void rechazaElSecretoDeHashDeDesarrolloOVacio() {
        assertThatThrownBy(() -> guard(OtpProperties.INSECURE_DEV_SECRET, OWN_KEY).checkSecrets())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("OTP_HASH_SECRET");
        assertThatThrownBy(() -> guard(" ", OWN_KEY).checkSecrets())
                .hasMessageContaining("OTP_HASH_SECRET");
    }

    @Test
    void rechazaLaClaveDeCifradoDeDesarrolloOVacia() {
        assertThatThrownBy(() -> guard("un-secreto-propio-largo", HmacProperties.INSECURE_DEV_KEY).checkSecrets())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("OTP_SECRET_ENCRYPTION_KEY");
        assertThatThrownBy(() -> guard("un-secreto-propio-largo", "").checkSecrets())
                .hasMessageContaining("OTP_SECRET_ENCRYPTION_KEY");
    }

    // De punta a punta: con el perfil prod y los valores por defecto del repositorio, la aplicacion no arranca.
    @Test
    void conPerfilProdYClavesDeDesarrolloLaAplicacionNoArranca() {
        assertThatThrownBy(() -> start("otp.hash-secret=" + OtpProperties.INSECURE_DEV_SECRET))
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .rootCause().hasMessageContaining("OTP_HASH_SECRET");
        assertThatThrownBy(() -> start("otp.hash-secret=un-secreto-propio-largo",
                "hmac.encryption-key=" + HmacProperties.INSECURE_DEV_KEY))
                .rootCause().hasMessageContaining("OTP_SECRET_ENCRYPTION_KEY");
    }

    @Test
    void conPerfilProdYClavesPropiasArrancaYNoEscribeCodigos() {
        try (ConfigurableApplicationContext context = start("otp.hash-secret=un-secreto-propio-largo",
                "hmac.encryption-key=" + OWN_KEY)) {
            assertThat(context.getBean(OtpProperties.class).logCodes()).isFalse();
        }
    }

    // Como argumentos de linea de comandos, para que ganen a application.yaml y a las variables de entorno.
    private static ConfigurableApplicationContext start(String... properties) {
        String[] args = Stream.concat(Stream.of("server.port=0"), Arrays.stream(properties))
                .map(property -> "--" + property)
                .toArray(String[]::new);
        return new SpringApplicationBuilder(OtpServiceApplication.class).profiles("prod").run(args);
    }
}
