package com.otpservice.otp.service.impl;

import com.otpservice.otp.dto.request.OtpGenerateRequest;
import com.otpservice.otp.dto.request.OtpVerifyRequest;
import com.otpservice.otp.dto.response.OtpGenerateResponse;
import com.otpservice.otp.dto.response.OtpVerifyResponse;
import com.otpservice.otp.dto.valueobject.Cellphone;
import com.otpservice.otp.dto.valueobject.OtpCode;
import com.otpservice.otp.dto.valueobject.TwilioCredentials;
import com.otpservice.otp.exception.ErrorCode;
import com.otpservice.otp.exception.OtpException;
import com.otpservice.otp.service.OtpService;
import com.otpservice.otp.service.TwilioOtpService;
import com.otpservice.otp.sms.TwilioSessionSmsSender;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// Reusa toda la logica de OtpService (generar, hashear, guardar en Mongo,
// verificar intentos/expiracion): lo unico que cambia respecto al flujo local
// es que el SMS sale por TwilioSessionSmsSender, con la cuenta que el
// visitante conecto, en vez del SmsSender global del servidor.
@Service
@RequiredArgsConstructor
public class TwilioOtpServiceImpl implements TwilioOtpService {

    private final OtpService otpService;
    private final TwilioSessionSmsSender sessionSmsSender;

    @Override
    public OtpGenerateResponse generateOtp(TwilioCredentials credentials, Cellphone cellphone, Integer digits, Integer durationSeconds) {
        OtpGenerateRequest request = new OtpGenerateRequest(cellphone, digits, durationSeconds);
        return otpService.generateOtp(request, (destination, message) -> sessionSmsSender.send(credentials, destination, message));
    }

    @Override
    public OtpVerifyResponse verifyOtp(Cellphone cellphone, String code) {
        try {
            return otpService.verifyOtp(new OtpVerifyRequest(cellphone, new OtpCode(code)));
        } catch (IllegalArgumentException exception) {
            throw new OtpException(ErrorCode.OTP_INVALID);
        }
    }
}
