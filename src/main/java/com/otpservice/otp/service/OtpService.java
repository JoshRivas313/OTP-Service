package com.otpservice.otp.service;

import com.otpservice.otp.dto.request.OtpGenerateRequest;
import com.otpservice.otp.dto.request.OtpVerifyRequest;
import com.otpservice.otp.dto.response.OtpGenerateResponse;
import com.otpservice.otp.dto.response.OtpVerifyResponse;
import com.otpservice.otp.sms.SmsSender;

public interface OtpService {

    OtpGenerateResponse generateOtp(OtpGenerateRequest request);

    OtpGenerateResponse generateOtp(OtpGenerateRequest request, SmsSender smsSender);

    OtpVerifyResponse verifyOtp(OtpVerifyRequest request);
}
