package com.otpservice.otp.adapter.out.persistence;

import com.otpservice.otp.adapter.out.persistence.document.HmacCredentialDocument;
import com.otpservice.otp.adapter.out.persistence.document.OtpDocument;
import com.otpservice.otp.application.dto.GenerateOtpResult;
import com.otpservice.otp.application.port.in.GenerateOtpUseCase;
import com.otpservice.otp.application.port.in.GenerateOtpUseCase.GenerateOtpCommand;
import com.otpservice.otp.application.port.in.VerifyOtpUseCase;
import com.otpservice.otp.application.port.in.VerifyOtpUseCase.VerifyOtpCommand;
import com.otpservice.otp.domain.exception.OtpAlreadyUsedException;
import com.otpservice.otp.domain.exception.OtpBlockedException;
import com.otpservice.otp.domain.exception.OtpDomainException;
import com.otpservice.otp.domain.exception.OtpInvalidatedException;
import com.otpservice.otp.application.exception.OtpNotFoundException;
import com.otpservice.otp.domain.valueobject.EmailAddress;
import com.otpservice.otp.domain.valueobject.OtpCode;
import com.otpservice.otp.domain.valueobject.OtpProtocol;
import com.otpservice.otp.domain.valueobject.Purpose;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// El almacenamiento de verdad: una MongoDB real en un contenedor. Aqui se comprueba lo que la version en memoria no puede
// demostrar: que las operaciones atomicas, los indices y la retencion funcionan en la base que usa el perfil mongo.
// Sin Docker el test se omite (en CI hay Docker).
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles({"dev", "mongo"})
@SpringBootTest(properties = {"otp.demo-mode=true"})
class MongoStorageIntegrationTest {

    @Container
    @ServiceConnection
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7");

    @Autowired
    private GenerateOtpUseCase generate;

    @Autowired
    private VerifyOtpUseCase verify;

    @Autowired
    private MongoTemplate mongo;

    private EmailAddress ana;
    private ExecutorService pool;

    @BeforeEach
    void setUp() {
        ana = new EmailAddress("ana-" + UUID.randomUUID().toString().substring(0, 8) + "@gmail.com");
        pool = Executors.newFixedThreadPool(16);
    }

    @AfterEach
    void tearDown() {
        pool.shutdownNow();
    }

    private GenerateOtpResult send(EmailAddress to, OtpProtocol protocol, Purpose purpose) {
        return generate.generate(new GenerateOtpCommand(to, protocol, purpose, 6, 30, null), (d, m) -> { });
    }

    private void check(EmailAddress to, OtpProtocol protocol, Purpose purpose, String code) {
        verify.verify(new VerifyOtpCommand(to, protocol, purpose, new OtpCode(code)));
    }

    private static String wrong(String right) {
        return right.equals("000000") ? "000001" : "000000";
    }

    // --- Los indices que la aplicacion promete crear al arrancar ---

    @Test
    void losIndicesDeBusquedaUnicidadYPurgaExisten() {
        List<IndexInfo> otps = mongo.indexOps(OtpDocument.class).getIndexInfo();
        List<IndexInfo> credentials = mongo.indexOps(HmacCredentialDocument.class).getIndexInfo();

        assertThat(otps).extracting(IndexInfo::getName).contains("otp_destination_purpose_generated_idx", "otp_purge_ttl_idx");
        assertThat(credentials).extracting(IndexInfo::getName)
                .contains("credential_destination_type_purpose_idx", "credential_purge_ttl_idx");
        assertThat(credentials).filteredOn(index -> index.getName().equals("credential_destination_type_purpose_idx"))
                .singleElement().satisfies(index -> assertThat(index.isUnique()).isTrue());
        assertThat(otps).filteredOn(index -> index.getName().equals("otp_purge_ttl_idx"))
                .singleElement().satisfies(index -> assertThat(index.getExpireAfter()).contains(Duration.ZERO));
        assertThat(credentials).filteredOn(index -> index.getName().equals("credential_purge_ttl_idx"))
                .singleElement().satisfies(index -> assertThat(index.getExpireAfter()).contains(Duration.ZERO));
    }

    // --- Ciclo de vida de un codigo, con cada protocolo ---

    @ParameterizedTest(name = "{0}: generar, verificar y reutilizar")
    @EnumSource(OtpProtocol.class)
    void unCodigoSeVerificaUnaVezYLaReutilizacionSeRechaza(OtpProtocol protocol) {
        String code = send(ana, protocol, Purpose.LOGIN).demoCode();

        check(ana, protocol, Purpose.LOGIN, code);

        assertThatThrownBy(() -> check(ana, protocol, Purpose.LOGIN, code)).isInstanceOf(OtpAlreadyUsedException.class);
    }

    @ParameterizedTest(name = "{0}: un codigo de un proposito no sirve para otro")
    @EnumSource(OtpProtocol.class)
    void elPropositoEsParteDeLaIdentidad(OtpProtocol protocol) {
        String code = send(ana, protocol, Purpose.PASSWORD_RECOVERY).demoCode();

        assertThatThrownBy(() -> check(ana, protocol, Purpose.PAYMENT_CONFIRMATION, code))
                .isInstanceOf(OtpNotFoundException.class);
        check(ana, protocol, Purpose.PASSWORD_RECOVERY, code);
    }

    @Test
    void pedirOtroOtpAleatorioInvalidaElAnteriorSoloDeEseProposito() {
        String first = send(ana, OtpProtocol.OTP, Purpose.LOGIN).demoCode();
        String payment = send(ana, OtpProtocol.OTP, Purpose.PAYMENT_CONFIRMATION).demoCode();
        send(ana, OtpProtocol.OTP, Purpose.LOGIN);

        assertThatThrownBy(() -> check(ana, OtpProtocol.OTP, Purpose.LOGIN, first))
                .isInstanceOfAny(OtpInvalidatedException.class, com.otpservice.otp.domain.exception.InvalidOtpException.class);
        check(ana, OtpProtocol.OTP, Purpose.PAYMENT_CONFIRMATION, payment);
    }

    @Test
    void elOtpAleatorioSoloGuardaElHashDelCodigo() {
        String code = send(ana, OtpProtocol.OTP, Purpose.LOGIN).demoCode();

        OtpDocument stored = mongo.findOne(Query.query(Criteria.where("destination").is(ana.getValue())), OtpDocument.class);

        assertThat(stored).isNotNull();
        assertThat(stored.getCodeHash()).hasSize(64).isNotEqualTo(code);
        assertThat(mongo.getCollection("otps").find().into(new ArrayList<>()).toString()).doesNotContain("\"" + code + "\"");
    }

    // --- HOTP: la ventana de codigos pendientes ---

    @Test
    void conDoceHotpPendientesElMasRecienteSeVerificaYLosMasViejosQuedanReemplazados() {
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            codes.add(send(ana, OtpProtocol.HOTP, Purpose.LOGIN).demoCode());
        }

        assertThatThrownBy(() -> check(ana, OtpProtocol.HOTP, Purpose.LOGIN, codes.get(0)))
                .isInstanceOf(OtpInvalidatedException.class);
        check(ana, OtpProtocol.HOTP, Purpose.LOGIN, codes.get(11));
        assertThatThrownBy(() -> check(ana, OtpProtocol.HOTP, Purpose.LOGIN, codes.get(5)))
                .isInstanceOf(OtpAlreadyUsedException.class);
    }

    // --- Bloqueo: persistente aunque se pida otro codigo ---

    @ParameterizedTest(name = "{0}: tres fallos bloquean y pedir otro no desbloquea")
    @EnumSource(value = OtpProtocol.class, names = {"HOTP", "TOTP"})
    void elBloqueoSobreviveAPedirOtroCodigo(OtpProtocol protocol) {
        String code = send(ana, protocol, Purpose.LOGIN).demoCode();
        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> check(ana, protocol, Purpose.LOGIN, wrong(code))).isInstanceOf(OtpDomainException.class);
        }
        assertThatThrownBy(() -> check(ana, protocol, Purpose.LOGIN, wrong(code))).isInstanceOf(OtpBlockedException.class);

        assertThatThrownBy(() -> send(ana, protocol, Purpose.LOGIN)).isInstanceOf(OtpBlockedException.class);
        assertThatThrownBy(() -> check(ana, protocol, Purpose.LOGIN, code)).isInstanceOf(OtpBlockedException.class);
    }

    // --- Atomicidad: varias verificaciones simultaneas, un solo exito ---

    @ParameterizedTest(name = "{0}: 16 verificaciones simultaneas, un solo exito")
    @EnumSource(OtpProtocol.class)
    void soloUnaVerificacionSimultaneaConsumeElCodigo(OtpProtocol protocol) throws Exception {
        for (int round = 0; round < 5; round++) {
            EmailAddress to = new EmailAddress("carrera-" + UUID.randomUUID().toString().substring(0, 8) + "@gmail.com");
            String code = send(to, protocol, Purpose.LOGIN).demoCode();

            List<Object> outcomes = race(() -> {
                check(to, protocol, Purpose.LOGIN, code);
                return Boolean.TRUE;
            });

            assertThat(outcomes).as("ronda %d", round).filteredOn(Boolean.TRUE::equals).hasSize(1);
            assertThat(outcomes).as("ronda %d", round).filteredOn(o -> !Boolean.TRUE.equals(o))
                    .hasSize(15).allMatch(OtpAlreadyUsedException.class::isInstance);
        }
    }

    @Test
    void dieciseisEmisionesSimultaneasDelMismoDestinoCreanUnaSolaCredencial() throws Exception {
        race(() -> send(ana, OtpProtocol.HOTP, Purpose.LOGIN).counter());

        long credentials = mongo.count(Query.query(Criteria.where("destination").is(ana.getValue())), HmacCredentialDocument.class);
        HmacCredentialDocument stored = mongo.findOne(
                Query.query(Criteria.where("destination").is(ana.getValue())), HmacCredentialDocument.class);

        assertThat(credentials).isEqualTo(1);
        assertThat(stored.getIssuedCounter()).isEqualTo(16);       // 16 hilos, 16 emisiones, ningun contador repetido
    }

    // --- Retencion: la credencial de un destino sin actividad se borra sola ---

    @Test
    void laCredencialLlevaSuFechaDePurgaYSeRenuevaConElUso() throws Exception {
        String code = send(ana, OtpProtocol.HOTP, Purpose.LOGIN).demoCode();
        Instant afterIssue = purgeAtOf(ana);

        assertThat(afterIssue).isAfter(Instant.now().plus(Duration.ofDays(29)));

        Thread.sleep(1100);
        check(ana, OtpProtocol.HOTP, Purpose.LOGIN, code);

        assertThat(purgeAtOf(ana)).isAfter(afterIssue);
    }

    private Instant purgeAtOf(EmailAddress to) {
        HmacCredentialDocument stored = mongo.findOne(
                Query.query(Criteria.where("destination").is(to.getValue())), HmacCredentialDocument.class);
        assertThat(stored).isNotNull();
        assertThat(stored.getPurgeAt()).isNotNull();
        return stored.getPurgeAt();
    }

    // Lanza la misma accion en 16 hilos a la vez y devuelve el resultado o la excepcion de cada uno.
    private List<Object> race(Callable<Object> action) throws Exception {
        int threads = 16;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                try {
                    return action.call();
                } catch (RuntimeException exception) {
                    return exception;
                }
            }));
        }
        ready.await();
        go.countDown();
        List<Object> outcomes = new ArrayList<>();
        for (Future<Object> future : futures) {
            outcomes.add(future.get(30, TimeUnit.SECONDS));
        }
        return outcomes;
    }
}
