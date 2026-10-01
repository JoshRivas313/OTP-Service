package com.otpservice.otp.adapter.in.http.dto.response;

import com.otpservice.otp.domain.exception.ErrorCode;

public record ErrorResponse(boolean success, String code, String message) {

    public static ErrorResponse of(ErrorCode code, String message) {
        return new ErrorResponse(false, code.name(), message);
    }
}
