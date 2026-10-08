package com.otpservice.otp.adapter.out;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.otpservice.otp.adapter.config.OtpProperties;
import com.otpservice.otp.adapter.out.email.ConsoleEmailSender;
import com.otpservice.otp.adapter.out.sms.ConsoleSmsSender;
import com.otpservice.otp.domain.valueobject.Cellphone;
import com.otpservice.otp.domain.valueobject.EmailAddress;
import com.otpservice.otp.support.TestOtpProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Los proveedores de consola solo escriben el codigo con otp.log-codes=true (desarrollo); en prod esta apagado.
class ConsoleSendersLogTest {

    private static final String MESSAGE = "Tu código de verificación es 482913. Vence en 30 segundos.";

    private final List<Runnable> detach = new ArrayList<>();

    @AfterEach
    void tearDown() {
        detach.forEach(Runnable::run);
    }

    private static OtpProperties properties(boolean logCodes) {
        return TestOtpProperties.otp().demoMode(false).logCodes(logCodes).build();
    }

    private List<String> capture(Class<?> type) {
        Logger logger = (Logger) LoggerFactory.getLogger(type);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        detach.add(() -> logger.detachAppender(appender));
        return new ListView(appender);
    }

    @Test
    void correoFueraDeDesarrolloNoEscribeElCodigo() {
        List<String> logs = capture(ConsoleEmailSender.class);

        new ConsoleEmailSender(properties(false)).send(new EmailAddress("jose@gmail.com"), MESSAGE);

        assertThat(logs).containsExactly("[EMAIL] Código enviado a j***@gmail.com");
        assertThat(String.join("\n", logs)).doesNotContain("482913").doesNotContain("jose@");
    }

    @Test
    void smsFueraDeDesarrolloNoEscribeElCodigo() {
        List<String> logs = capture(ConsoleSmsSender.class);

        new ConsoleSmsSender(properties(false)).send(new Cellphone("987654321"), MESSAGE);

        assertThat(logs).hasSize(1);
        assertThat(logs.getFirst()).startsWith("[SMS] Código enviado a ").endsWith("321")
                .doesNotContain("482913").doesNotContain("987654321");
    }

    @Test
    void enDesarrolloSiEscribeElMensajeCompleto() {
        List<String> logs = capture(ConsoleEmailSender.class);

        new ConsoleEmailSender(properties(true)).send(new EmailAddress("jose@gmail.com"), MESSAGE);

        assertThat(logs).singleElement().asString().startsWith("[DEV][EMAIL]").contains("482913");
    }

    // Vista de los mensajes ya formateados del appender.
    private static final class ListView extends java.util.AbstractList<String> {
        private final ListAppender<ILoggingEvent> appender;

        ListView(ListAppender<ILoggingEvent> appender) {
            this.appender = appender;
        }

        @Override
        public String get(int index) {
            return appender.list.get(index).getFormattedMessage();
        }

        @Override
        public int size() {
            return appender.list.size();
        }
    }
}
