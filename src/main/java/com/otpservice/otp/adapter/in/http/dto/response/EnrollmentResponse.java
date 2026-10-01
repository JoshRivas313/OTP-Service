package com.otpservice.otp.adapter.in.http.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.otpservice.otp.application.dto.EnrollmentResult;
import com.otpservice.otp.domain.valueobject.HmacType;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record EnrollmentResponse(boolean success, String message, HmacType type, String otpauthUri,
                                 String qrSvg, String secretBase32, int digits, Integer periodSeconds, Long counter) {

    public static EnrollmentResponse from(EnrollmentResult result) {
        return new EnrollmentResponse(true, "Escaneá el código QR con tu app y confirmá con el primer código",
                result.type(), result.otpauthUri(), result.qrSvg(), result.secretBase32(), result.digits(),
                result.periodSeconds(), result.counter());
    }
}
