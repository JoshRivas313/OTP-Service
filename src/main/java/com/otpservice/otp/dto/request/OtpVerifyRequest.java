package com.otpservice.otp.dto.request;

import com.otpservice.otp.dto.valueobject.Cellphone;
import com.otpservice.otp.dto.valueobject.OtpCode;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class OtpVerifyRequest {

    @NotNull(message = "El celular es obligatorio")
    private Cellphone cellphone;

    @NotNull(message = "El código es obligatorio")
    private OtpCode code;
}
