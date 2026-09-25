package com.otpservice.otp.dto.request;

import com.otpservice.otp.dto.valueobject.Cellphone;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

// digits es opcional: si se envia, el backend genera el codigo (OtpCode.generate)
// y se lo pasa a Twilio via customCode, en vez de dejar que Twilio genere el
// suyo. Si no se envia, Twilio genera su propio codigo con la longitud del
// Service.
//
// No existe un durationSeconds aca a proposito: Twilio Verify no acepta un
// tiempo de expiracion por request bajo ninguna circunstancia (ni siquiera
// con customCode), solo se configura una vez en el Service desde el
// dashboard de Twilio. Agregarlo aca seria un parametro decorativo que el
// backend recibiria pero nunca usaria.
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class TwilioOtpGenerateRequest {

    @NotNull(message = "El celular es obligatorio")
    private Cellphone cellphone;

    @Min(value = 4, message = "Mínimo 4 dígitos")
    @Max(value = 10, message = "Máximo 10 dígitos")
    private Integer digits;
}
