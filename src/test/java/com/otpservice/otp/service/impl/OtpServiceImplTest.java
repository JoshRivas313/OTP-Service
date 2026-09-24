package com.otpservice.otp.service.impl;

import com.otpservice.otp.config.OtpProperties;
import com.otpservice.otp.document.OtpDocument;
import com.otpservice.otp.dto.request.OtpGenerateRequest;
import com.otpservice.otp.dto.request.OtpVerifyRequest;
import com.otpservice.otp.dto.valueobject.Cellphone;
import com.otpservice.otp.dto.valueobject.OtpCode;
import com.otpservice.otp.dto.valueobject.ValidityWindow;
import com.otpservice.otp.dto.valueobject.VerificationStatus;
import com.otpservice.otp.exception.ErrorCode;
import com.otpservice.otp.exception.OtpException;
import com.otpservice.otp.repository.OtpRepository;
import com.otpservice.otp.security.CodeHasher;
import com.otpservice.otp.sms.SmsSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OtpServiceImplTest {

    private static final Instant AHORA = Instant.parse("2026-01-01T10:00:00Z");
    private static final String CELULAR = "987654321";
    private static final String CELULAR_NORMALIZADO = "+51987654321";
    private static final String ID = "otp-1";

    private OtpRepository otpRepository;
    private SmsSender smsSender;
    private CodeHasher codeHasher;
    private OtpServiceImpl service;

    @BeforeEach
    void setUp() {
        OtpProperties properties = new OtpProperties(6, 30, 3, 86400,
                "Tu código es %s. Vence en %d segundos.", "secreto-de-prueba", false);
        otpRepository = mock(OtpRepository.class);
        smsSender = mock(SmsSender.class);
        codeHasher = new CodeHasher(properties);

        service = new OtpServiceImpl(otpRepository, codeHasher, smsSender, properties,
                Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    @Test
    void generaElOtpYLoEntregaPorSms() {
        when(otpRepository.save(any(OtpDocument.class))).thenAnswer(i -> i.getArgument(0));

        service.generateOtp(new OtpGenerateRequest(new Cellphone(CELULAR), null, null));

        var mensaje = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(smsSender).send(any(Cellphone.class), mensaje.capture());
        assertThat(mensaje.getValue()).containsPattern("\\d{6}");
    }

    @Test
    void verificaUnCodigoCorrecto() {
        OtpDocument otp = otp("472981", new VerificationStatus());
        when(otpRepository.findFirstByCellphoneOrderByValidityWindowGeneratedAtDesc(CELULAR_NORMALIZADO))
                .thenReturn(Optional.of(otp));
        when(otpRepository.claimIfMatches(eq(ID), eq(codeHasher.hash("472981")), any(Instant.class), anyInt()))
                .thenReturn(Optional.of(otp));

        assertThat(service.verifyOtp(verificar("472981")).success()).isTrue();
    }

    @Test
    void rechazaUnCodigoExpirado() {
        OtpDocument expirado = new OtpDocument(ID, CELULAR_NORMALIZADO, codeHasher.hash("472981"), 6,
                ValidityWindow.from(AHORA.minusSeconds(60), 30), new VerificationStatus(), AHORA.plusSeconds(3600));
        when(otpRepository.findFirstByCellphoneOrderByValidityWindowGeneratedAtDesc(CELULAR_NORMALIZADO))
                .thenReturn(Optional.of(expirado));

        assertThatThrownBy(() -> service.verifyOtp(verificar("472981")))
                .isInstanceOf(OtpException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.OTP_EXPIRED);
    }

    @Test
    void bloqueaAlLlegarAlMaximoDeIntentos() {
        OtpDocument otp = otp("472981", new VerificationStatus(2, false, false));
        when(otpRepository.findFirstByCellphoneOrderByValidityWindowGeneratedAtDesc(CELULAR_NORMALIZADO))
                .thenReturn(Optional.of(otp));
        when(otpRepository.claimIfMatches(anyString(), anyString(), any(Instant.class), anyInt()))
                .thenReturn(Optional.empty());
        when(otpRepository.registerFailedAttempt(ID))
                .thenReturn(Optional.of(otp("472981", new VerificationStatus(3, false, false))));

        assertThatThrownBy(() -> service.verifyOtp(verificar("000000")))
                .isInstanceOf(OtpException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.OTP_BLOCKED);
    }

    private OtpDocument otp(String codigo, VerificationStatus status) {
        return new OtpDocument(ID, CELULAR_NORMALIZADO, codeHasher.hash(codigo), 6,
                ValidityWindow.from(AHORA, 30), status, AHORA.plusSeconds(3600));
    }

    private OtpVerifyRequest verificar(String codigo) {
        return new OtpVerifyRequest(new Cellphone(CELULAR), new OtpCode(codigo));
    }
}
