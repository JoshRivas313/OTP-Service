package com.otpservice.otp.adapter.in.http.dto.request;

import com.otpservice.otp.domain.valueobject.Cellphone;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class TwilioOtpVerifyRequest {

    @NotNull(message = "El celular es obligatorio")
    private Cellphone cellphone;

    @NotBlank(message = "El código es obligatorio")
    private String code;
}
