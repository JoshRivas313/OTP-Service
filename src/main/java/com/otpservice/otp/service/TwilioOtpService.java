package com.otpservice.otp.service;

import com.otpservice.otp.dto.response.OtpGenerateResponse;
import com.otpservice.otp.dto.response.OtpVerifyResponse;
import com.otpservice.otp.dto.valueobject.Cellphone;
import com.otpservice.otp.dto.valueobject.TwilioCredentials;

public interface TwilioOtpService {

    OtpGenerateResponse generateOtp(TwilioCredentials credentials, Cellphone cellphone, Integer digits, Integer durationSeconds);

    OtpVerifyResponse verifyOtp(Cellphone cellphone, String code);
}
