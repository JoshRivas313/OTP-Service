package com.otpservice.otp.adapter.out.persistence.memory;

import com.otpservice.otp.domain.model.HmacCredential;
import com.otpservice.otp.domain.valueobject.EncryptedSecret;
import com.otpservice.otp.domain.valueobject.HmacType;
import com.otpservice.otp.domain.valueobject.Purpose;
import com.otpservice.otp.support.TestOtpProperties;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

// Al llegar al tope se expulsa la credencial menos usada, no la mas antigua por insercion.
class InMemoryCredentialAdapterTest {

    private static final Instant NOW = Instant.parse("2026-10-07T12:00:00Z");

    private static HmacCredential credential(String destination) {
        return HmacCredential.create(destination, HmacType.HOTP, Purpose.LOGIN,
                new EncryptedSecret(new byte[]{1, 2, 3}, new byte[]{4, 5, 6}), 6, 0, NOW);
    }

    private static InMemoryCredentialAdapter adapterWithRoomFor(int entries) {
        return new InMemoryCredentialAdapter(TestOtpProperties.otp().memoryMaxEntries(entries).build());
    }

    private static boolean exists(InMemoryCredentialAdapter adapter, String destination) {
        return adapter.find(destination, HmacType.HOTP, Purpose.LOGIN).isPresent();
    }

    @Test
    void sinUsoSeExpulsaLaMasAntigua() {
        InMemoryCredentialAdapter adapter = adapterWithRoomFor(2);
        adapter.createIfAbsent(credential("a@gmail.com"));
        adapter.createIfAbsent(credential("b@gmail.com"));

        adapter.createIfAbsent(credential("c@gmail.com"));

        assertThat(exists(adapter, "a@gmail.com")).isFalse();
        assertThat(exists(adapter, "b@gmail.com")).isTrue();
        assertThat(exists(adapter, "c@gmail.com")).isTrue();
    }

    @Test
    void unaCredencialConsultadaSobreviveAunqueSeaLaMasAntigua() {
        InMemoryCredentialAdapter adapter = adapterWithRoomFor(2);
        adapter.createIfAbsent(credential("a@gmail.com"));
        adapter.createIfAbsent(credential("b@gmail.com"));
        adapter.find("a@gmail.com", HmacType.HOTP, Purpose.LOGIN);        // a se usa; b queda como la menos usada

        adapter.createIfAbsent(credential("c@gmail.com"));

        assertThat(exists(adapter, "a@gmail.com")).isTrue();
        assertThat(exists(adapter, "b@gmail.com")).isFalse();
    }

    @Test
    void emitirUnCodigoCuentaComoUso() {
        InMemoryCredentialAdapter adapter = adapterWithRoomFor(2);
        String idOfA = adapter.createIfAbsent(credential("a@gmail.com")).getId();
        adapter.createIfAbsent(credential("b@gmail.com"));
        adapter.issue(idOfA, 6, 0, true);                                  // un usuario activo emite sobre a

        adapter.createIfAbsent(credential("c@gmail.com"));

        assertThat(exists(adapter, "a@gmail.com")).isTrue();
        assertThat(exists(adapter, "b@gmail.com")).isFalse();
    }
}
