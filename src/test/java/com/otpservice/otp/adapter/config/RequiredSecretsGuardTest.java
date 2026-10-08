package com.otpservice.otp.adapter.config;

import com.otpservice.otp.OtpServiceApplication;
import com.otpservice.otp.support.TestOtpProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.mock.env.MockEnvironment;

import java.util.Arrays;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequiredSecretsGuardTest {

    private static final String OWN_SECRET = "un-secreto-propio-largo";
    private static final String OWN_KEY = "q2Zt0f1pY8m3vXc7Lr5sWb9nHk4Jd6Ae2Gu0Ti8Oy1M=";

    private static RequiredSecretsGuard guard(String hashSecret, String encryptionKey, String... profiles) {
        OtpProperties otp = TestOtpProperties.otp().hashSecret(hashSecret).build();
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        return new RequiredSecretsGuard(otp, new HmacProperties(1, 10, encryptionKey), environment);
    }

    @Test
    void aceptaClavesPropiasConCualquierPerfil() {
        assertThatCode(() -> guard(OWN_SECRET, OWN_KEY).checkSecrets()).doesNotThrowAnyException();
        assertThatCode(() -> guard(OWN_SECRET, OWN_KEY, "prod").checkSecrets()).doesNotThrowAnyException();
    }

    @Test
    void sinClavesNoArrancaYElMensajeDiceComoProbarEnLocal() {
        assertThatThrownBy(() -> guard("", OWN_KEY).checkSecrets())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OTP_HASH_SECRET")
                .hasMessageContaining("SPRING_PROFILES_ACTIVE=dev");
        assertThatThrownBy(() -> guard(OWN_SECRET, " ").checkSecrets())
                .hasMessageContaining("OTP_SECRET_ENCRYPTION_KEY")
                .hasMessageContaining("SPRING_PROFILES_ACTIVE=dev");
        assertThatThrownBy(() -> guard(null, OWN_KEY, "prod").checkSecrets())
                .hasMessageContaining("OTP_HASH_SECRET");
    }

    @Test
    void lasClavesDeEjemploSoloValenConElPerfilDev() {
        assertThatThrownBy(() -> guard(OtpProperties.INSECURE_DEV_SECRET, OWN_KEY).checkSecrets())
                .hasMessageContaining("OTP_HASH_SECRET");
        assertThatThrownBy(() -> guard(OWN_SECRET, HmacProperties.INSECURE_DEV_KEY, "prod").checkSecrets())
                .hasMessageContaining("OTP_SECRET_ENCRYPTION_KEY");

        assertThatCode(() -> guard(OtpProperties.INSECURE_DEV_SECRET, HmacProperties.INSECURE_DEV_KEY, "dev")
                .checkSecrets()).doesNotThrowAnyException();
    }

    @Test
    void elPerfilDevNoDisculpaUnaClaveVacia() {
        assertThatThrownBy(() -> guard("", HmacProperties.INSECURE_DEV_KEY, "dev").checkSecrets())
                .hasMessageContaining("OTP_HASH_SECRET");
    }

    @Test
    void elPerfilDevNoPuedeConvivirConProd() {
        assertThatThrownBy(() -> guard(OWN_SECRET, OWN_KEY, "dev", "prod").checkSecrets())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dev no puede combinarse con prod");
    }

    // De punta a punta, con la configuracion real del repositorio. Las claves vacias se fuerzan por argumento para que
    // una variable de entorno de la maquina no cambie el resultado.
    @Test
    void sinClavesLaAplicacionNoArrancaAunqueElPerfilSeaProd() {
        assertThatThrownBy(() -> start("prod", "otp.hash-secret=", "hmac.encryption-key="))
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Falta OTP_HASH_SECRET")
                .hasMessageContaining("SPRING_PROFILES_ACTIVE=dev");
    }

    @Test
    void sinPerfilNiClavesLaAplicacionNoArranca() {
        assertThatThrownBy(() -> start(null, "otp.hash-secret=", "hmac.encryption-key="))
                .rootCause().hasMessageContaining("Falta OTP_HASH_SECRET");
    }

    @Test
    void conClavesPropiasArrancaConLosValoresSegurosPorDefecto() {
        try (ConfigurableApplicationContext context = start("prod", "otp.hash-secret=" + OWN_SECRET,
                "hmac.encryption-key=" + OWN_KEY)) {
            OtpProperties otp = context.getBean(OtpProperties.class);
            assertThat(otp.logCodes()).isFalse();
            assertThat(otp.customMessageEnabled()).isFalse();
            assertThat(otp.dailySendLimit()).isZero();
        }
    }

    @Test
    void elPerfilDevArrancaSinConfigurarNadaYEnciendeLasAyudasDeDesarrollo() {
        try (ConfigurableApplicationContext context = start("dev")) {
            OtpProperties otp = context.getBean(OtpProperties.class);
            assertThat(otp.hashSecret()).isNotBlank();
            assertThat(otp.logCodes()).isTrue();
            assertThat(otp.customMessageEnabled()).isTrue();
        }
    }

    // Como argumentos de linea de comandos, para que ganen a application.yaml y a las variables de entorno.
    private static ConfigurableApplicationContext start(String profile, String... properties) {
        String[] args = Stream.concat(Stream.of("server.port=0"), Arrays.stream(properties))
                .map(property -> "--" + property)
                .toArray(String[]::new);
        SpringApplicationBuilder builder = new SpringApplicationBuilder(OtpServiceApplication.class);
        if (profile != null) {
            builder.profiles(profile);
        }
        return builder.run(args);
    }
}
