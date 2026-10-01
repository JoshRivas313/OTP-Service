package com.otpservice.otp.adapter.in.http.dto.request;

import com.otpservice.otp.domain.valueobject.HmacType;
import com.otpservice.otp.domain.valueobject.EmailAddress;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class AuthenticatorCodeRequest {

    @NotNull(message = "El correo es obligatorio")
    private EmailAddress email;

    @NotNull(message = "El tipo es obligatorio: HOTP o TOTP")
    private HmacType type;

    @NotBlank(message = "El código es obligatorio")
    @Pattern(regexp = "\\d{6,8}", message = "El código de la app tiene 6 u 8 dígitos")
    private String code;
}
