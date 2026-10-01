package com.otpservice.otp.application.dto;

import com.otpservice.otp.domain.valueobject.HmacType;

public record EnrollmentResult(HmacType type, String otpauthUri, String qrSvg, String secretBase32,
                               int digits, Integer periodSeconds, Long counter) {
}
