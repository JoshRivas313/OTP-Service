package com.otpservice.otp.adapter.in.http.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.otpservice.otp.application.dto.VerifyOtpResult;
import com.otpservice.otp.domain.valueobject.OtpProtocol;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OtpVerifyResponse(boolean success, String message, OtpProtocol type, Long counter, Long timeStep) {

    public static OtpVerifyResponse from(VerifyOtpResult result) {
        return new OtpVerifyResponse(result.success(), result.message(), result.protocol(),
                result.counter(), result.timeStep());
    }
}
