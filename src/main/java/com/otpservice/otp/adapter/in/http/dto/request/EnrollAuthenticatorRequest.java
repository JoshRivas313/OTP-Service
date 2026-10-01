package com.otpservice.otp.adapter.in.http.dto.request;

import com.otpservice.otp.domain.valueobject.HmacType;
import com.otpservice.otp.domain.valueobject.EmailAddress;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class EnrollAuthenticatorRequest {

    @NotNull(message = "El correo es obligatorio")
    private EmailAddress email;

    @NotNull(message = "El tipo es obligatorio: HOTP o TOTP")
    private HmacType type;

    private Integer digits;

    private Integer periodSeconds;
}
