package com.otpservice.otp.service.impl;

import com.otpservice.otp.dto.valueobject.Cellphone;
import com.otpservice.otp.dto.valueobject.TwilioCredentials;
import com.otpservice.otp.exception.ErrorCode;
import com.otpservice.otp.exception.OtpException;
import com.otpservice.otp.sms.TwilioVerifyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TwilioOtpServiceImplTest {

    private static final TwilioCredentials CREDENTIALS = new TwilioCredentials(
            "AC" + "a".repeat(32), "b".repeat(32), "VA" + "c".repeat(32));
    private static final Cellphone CELULAR = new Cellphone("987654321");

    private TwilioVerifyService twilioVerifyService;
    private TwilioOtpServiceImpl service;

    @BeforeEach
    void setUp() {
        twilioVerifyService = mock(TwilioVerifyService.class);
        service = new TwilioOtpServiceImpl(twilioVerifyService);
    }

    @Test
    void generarPideAVerifyQueEnvieElCodigo() {
        assertThat(service.generateOtp(CREDENTIALS, CELULAR).success()).isTrue();
        verify(twilioVerifyService).sendVerificationCode(CREDENTIALS, CELULAR);
    }

    @Test
    void verificarUnCodigoAprobadoDevuelveExito() {
        when(twilioVerifyService.checkVerificationCode(CREDENTIALS, CELULAR, "123456")).thenReturn(true);
        assertThat(service.verifyOtp(CREDENTIALS, CELULAR, "123456").success()).isTrue();
    }

    @Test
    void verificarUnCodigoRechazadoLanzaOtpInvalid() {
        when(twilioVerifyService.checkVerificationCode(CREDENTIALS, CELULAR, "000000")).thenReturn(false);
        assertThatThrownBy(() -> service.verifyOtp(CREDENTIALS, CELULAR, "000000"))
                .isInstanceOf(OtpException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.OTP_INVALID);
    }
}
