package com.otpservice.otp.dto.request;

import com.otpservice.otp.dto.valueobject.Cellphone;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class TwilioOtpGenerateRequest {

    @NotNull(message = "El celular es obligatorio")
    private Cellphone cellphone;
}
