package com.otpservice.otp.service.impl;

import com.otpservice.otp.dto.request.OtpGenerateRequest;
import com.otpservice.otp.dto.request.OtpVerifyRequest;
import com.otpservice.otp.dto.response.OtpGenerateResponse;
import com.otpservice.otp.dto.response.OtpVerifyResponse;
import com.otpservice.otp.dto.valueobject.Cellphone;
import com.otpservice.otp.dto.valueobject.TwilioCredentials;
import com.otpservice.otp.exception.ErrorCode;
import com.otpservice.otp.exception.OtpException;
import com.otpservice.otp.service.OtpService;
import com.otpservice.otp.sms.SmsSender;
import com.otpservice.otp.sms.TwilioSessionSmsSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TwilioOtpServiceImplTest {

    private static final TwilioCredentials CREDENTIALS = new TwilioCredentials(
            "AC" + "a".repeat(32), "b".repeat(32), "VA" + "c".repeat(32), "+15017122661");
    private static final Cellphone CELULAR = new Cellphone("987654321");

    private OtpService otpService;
    private TwilioSessionSmsSender sessionSmsSender;
    private TwilioOtpServiceImpl service;

    @BeforeEach
    void setUp() {
        otpService = mock(OtpService.class);
        sessionSmsSender = mock(TwilioSessionSmsSender.class);
        service = new TwilioOtpServiceImpl(otpService, sessionSmsSender);
    }

    @Test
    void generarDelegaEnOtpServiceConDigitsYDuration() {
        when(otpService.generateOtp(any(OtpGenerateRequest.class), any(SmsSender.class)))
                .thenReturn(OtpGenerateResponse.sent());
        ArgumentCaptor<OtpGenerateRequest> captor = ArgumentCaptor.forClass(OtpGenerateRequest.class);

        OtpGenerateResponse response = service.generateOtp(CREDENTIALS, CELULAR, 8, 120);

        assertThat(response.success()).isTrue();
        verify(otpService).generateOtp(captor.capture(), any(SmsSender.class));
        assertThat(captor.getValue().getCellphone()).isEqualTo(CELULAR);
        assertThat(captor.getValue().getDigits()).isEqualTo(8);
        assertThat(captor.getValue().getDurationSeconds()).isEqualTo(120);
    }

    @Test
    void generarMandaElSmsPorLaCuentaDeLaSesion() {
        when(otpService.generateOtp(any(OtpGenerateRequest.class), any(SmsSender.class)))
                .thenAnswer(invocation -> {
                    SmsSender sender = invocation.getArgument(1);
                    sender.send(CELULAR, "hola");
                    return OtpGenerateResponse.sent();
                });

        service.generateOtp(CREDENTIALS, CELULAR, null, null);

        verify(sessionSmsSender).send(CREDENTIALS, CELULAR, "hola");
    }

    @Test
    void verificarUnCodigoAprobadoDevuelveExito() {
        when(otpService.verifyOtp(any(OtpVerifyRequest.class)))
                .thenReturn(OtpVerifyResponse.verified());
        ArgumentCaptor<OtpVerifyRequest> captor = ArgumentCaptor.forClass(OtpVerifyRequest.class);

        assertThat(service.verifyOtp(CELULAR, "123456").success()).isTrue();

        verify(otpService).verifyOtp(captor.capture());
        assertThat(captor.getValue().getCellphone()).isEqualTo(CELULAR);
        assertThat(captor.getValue().getCode().getValue()).isEqualTo("123456");
    }

    @Test
    void verificarUnCodigoConFormatoInvalidoLanzaOtpInvalid() {
        assertThatThrownBy(() -> service.verifyOtp(CELULAR, "abc"))
                .isInstanceOf(OtpException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.OTP_INVALID);
    }
}
