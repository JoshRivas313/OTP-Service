package com.otpservice.otp.application.usecase;

import com.otpservice.otp.domain.exception.OtpAlreadyUsedException;
import com.otpservice.otp.domain.valueobject.EmailAddress;
import com.otpservice.otp.domain.valueobject.OtpProtocol;
import com.otpservice.otp.domain.valueobject.Purpose;
import com.otpservice.otp.support.ProtocolStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

// Varias verificaciones simultaneas del mismo codigo correcto: solo una puede consumirlo.
class ConcurrentVerificationTest {

    private static final int THREADS = 16;
    private static final int ROUNDS = 50;

    private ExecutorService pool;

    @BeforeEach
    void setUp() {
        pool = Executors.newFixedThreadPool(THREADS);
    }

    @AfterEach
    void tearDown() {
        pool.shutdownNow();
    }

    @ParameterizedTest(name = "{0}: una sola verificacion gana")
    @EnumSource(OtpProtocol.class)
    void unaSolaVerificacionConsumeElCodigo(OtpProtocol protocol) throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            ProtocolStack stack = new ProtocolStack();
            EmailAddress to = new EmailAddress("carrera" + round + "@gmail.com");
            String code = stack.send(to, protocol, Purpose.LOGIN, 30).demoCode();

            List<Object> outcomes = race(() -> stack.check(to, protocol, Purpose.LOGIN, code).success());

            assertThat(outcomes).as("ronda %d", round).filteredOn(Boolean.TRUE::equals).hasSize(1);
            assertThat(outcomes).as("ronda %d", round).filteredOn(o -> !Boolean.TRUE.equals(o))
                    .hasSize(THREADS - 1)
                    .allMatch(OtpAlreadyUsedException.class::isInstance);
        }
    }

    // Lanza la misma verificacion en todos los hilos a la vez y devuelve el resultado o la excepcion de cada uno.
    private List<Object> race(java.util.concurrent.Callable<Boolean> verification) throws Exception {
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                try {
                    return verification.call();
                } catch (RuntimeException e) {
                    return e;
                }
            }));
        }
        ready.await();
        go.countDown();
        List<Object> outcomes = new ArrayList<>();
        for (Future<Object> future : futures) {
            outcomes.add(future.get(10, TimeUnit.SECONDS));
        }
        return outcomes;
    }
}
