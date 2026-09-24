package com.otpservice.otp.service;

import com.otpservice.otp.dto.request.OtpGenerateRequest;
import com.otpservice.otp.dto.request.OtpVerifyRequest;
import com.otpservice.otp.dto.response.OtpGenerateResponse;
import com.otpservice.otp.dto.response.OtpVerifyResponse;

public interface OtpService {

    OtpGenerateResponse generateOtp(OtpGenerateRequest request);

    OtpVerifyResponse verifyOtp(OtpVerifyRequest request);
}
