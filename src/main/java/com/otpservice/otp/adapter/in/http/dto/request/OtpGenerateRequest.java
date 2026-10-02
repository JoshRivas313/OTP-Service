package com.otpservice.otp.adapter.in.http.dto.request;

import com.otpservice.otp.domain.valueobject.OtpCode;
import com.otpservice.otp.domain.valueobject.OtpProtocol;
import com.otpservice.otp.domain.valueobject.Purpose;
import com.otpservice.otp.domain.valueobject.Cellphone;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class OtpGenerateRequest {

    @NotNull(message = "El celular es obligatorio")
    @Valid
    private Cellphone cellphone;

    private OtpProtocol type;

    private Purpose purpose;

    @Min(value = OtpCode.MIN_LENGTH, message = RequestRules.DIGITS_MIN_MESSAGE)
    @Max(value = OtpCode.MAX_LENGTH, message = RequestRules.DIGITS_MAX_MESSAGE)
    private Integer digits;

    @Positive(message = RequestRules.DURATION_POSITIVE_MESSAGE)
    @Max(value = RequestRules.MAX_DURATION_SECONDS, message = RequestRules.DURATION_MAX_MESSAGE)
    private Integer durationSeconds;
}
