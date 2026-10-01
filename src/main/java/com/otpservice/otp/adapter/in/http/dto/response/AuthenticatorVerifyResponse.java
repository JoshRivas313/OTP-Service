package com.otpservice.otp.adapter.in.http.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.otpservice.otp.application.dto.AuthenticatorVerifyResult;
import com.otpservice.otp.domain.valueobject.HmacType;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthenticatorVerifyResponse(boolean success, String message, HmacType type,
                                          Long timeStep, Long counter) {

    public static AuthenticatorVerifyResponse from(AuthenticatorVerifyResult result) {
        return new AuthenticatorVerifyResponse(result.success(), result.message(), result.type(),
                result.timeStep(), result.counter());
    }
}
