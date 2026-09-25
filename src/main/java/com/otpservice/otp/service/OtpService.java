package com.otpservice.otp.service;

import com.otpservice.otp.dto.request.OtpGenerateRequest;
import com.otpservice.otp.dto.request.OtpVerifyRequest;
import com.otpservice.otp.dto.response.OtpGenerateResponse;
import com.otpservice.otp.dto.response.OtpVerifyResponse;
import com.otpservice.otp.sms.SmsSender;

public interface OtpService {

    OtpGenerateResponse generateOtp(OtpGenerateRequest request);

    // Igual que generateOtp(request), pero manda el SMS con smsSender en vez
    // del SmsSender global inyectado. Lo usa el flujo de Twilio conectado en
    // sesion, donde cada visitante tiene su propia cuenta (no la del server).
    OtpGenerateResponse generateOtp(OtpGenerateRequest request, SmsSender smsSender);

    OtpVerifyResponse verifyOtp(OtpVerifyRequest request);
}
