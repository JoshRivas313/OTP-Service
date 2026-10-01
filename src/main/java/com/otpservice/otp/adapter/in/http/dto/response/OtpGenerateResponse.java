package com.otpservice.otp.adapter.in.http.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.otpservice.otp.application.dto.GenerateOtpResult;
import com.otpservice.otp.domain.valueobject.OtpProtocol;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OtpGenerateResponse(boolean success, String message, String demoCode, OtpProtocol type,
                                  Long expiresInSeconds, Long counter, Long timeStep) {

    public static OtpGenerateResponse from(GenerateOtpResult result) {
        return new OtpGenerateResponse(result.success(), result.message(), result.demoCode(), result.protocol(),
                result.expiresInSeconds(), result.counter(), result.timeStep());
    }
}
