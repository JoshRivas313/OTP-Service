package com.otpservice.otp.dto.request;

import com.otpservice.otp.dto.valueobject.Cellphone;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

// El codigo va como String plano, no como OtpCode: ese VO valida el formato
// de los codigos que generamos nosotros, y el de Twilio Verify es un
// sistema externo con sus propias reglas.
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class TwilioOtpVerifyRequest {

    @NotNull(message = "El celular es obligatorio")
    private Cellphone cellphone;

    @NotBlank(message = "El código es obligatorio")
    private String code;
}
