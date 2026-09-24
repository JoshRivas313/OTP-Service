package com.otpservice.otp.service.impl;

import com.otpservice.otp.dto.response.OtpGenerateResponse;
import com.otpservice.otp.dto.response.OtpVerifyResponse;
import com.otpservice.otp.dto.valueobject.Cellphone;
import com.otpservice.otp.dto.valueobject.TwilioCredentials;
import com.otpservice.otp.exception.ErrorCode;
import com.otpservice.otp.exception.OtpException;
import com.otpservice.otp.service.TwilioOtpService;
import com.otpservice.otp.sms.TwilioVerifyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TwilioOtpServiceImpl implements TwilioOtpService {

    private final TwilioVerifyService twilioVerifyService;

    @Override
    public OtpGenerateResponse generateOtp(TwilioCredentials credentials, Cellphone cellphone) {
        twilioVerifyService.sendVerificationCode(credentials, cellphone);
        return OtpGenerateResponse.sent();
    }

    @Override
    public OtpVerifyResponse verifyOtp(TwilioCredentials credentials, Cellphone cellphone, String code) {
        boolean approved = twilioVerifyService.checkVerificationCode(credentials, cellphone, code);
        if (!approved) {
            throw new OtpException(ErrorCode.OTP_INVALID);
        }
        return OtpVerifyResponse.verified();
    }
}
