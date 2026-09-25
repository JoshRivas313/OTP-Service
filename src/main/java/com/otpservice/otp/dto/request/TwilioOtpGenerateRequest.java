package com.otpservice.otp.dto.request;

import com.otpservice.otp.dto.valueobject.Cellphone;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

// digits y durationSeconds son opcionales, con los mismos limites que
// OtpGenerateRequest (el backend que realmente los usa: el OTP se genera y
// guarda en Mongo igual que en /otps, solo cambia por donde sale el SMS).
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class TwilioOtpGenerateRequest {

    @NotNull(message = "El celular es obligatorio")
    private Cellphone cellphone;

    @Min(value = 4, message = "Mínimo 4 dígitos")
    @Max(value = 10, message = "Máximo 10 dígitos")
    private Integer digits;

    @Positive(message = "La duración debe ser mayor a 0 segundos")
    @Max(value = 86400, message = "La duración no puede superar un día")
    private Integer durationSeconds;
}
