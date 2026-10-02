package com.otpservice.otp.adapter.in.http.dto.request;

import com.otpservice.otp.domain.valueobject.OtpCode;
import com.otpservice.otp.domain.valueobject.OtpProtocol;
import com.otpservice.otp.domain.valueobject.Purpose;
import com.otpservice.otp.domain.valueobject.EmailAddress;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class EmailOtpGenerateRequest {

    @NotNull(message = "El correo es obligatorio")
    private EmailAddress email;

    private OtpProtocol type;

    private Purpose purpose;

    @Min(value = OtpCode.MIN_LENGTH, message = RequestRules.DIGITS_MIN_MESSAGE)
    @Max(value = OtpCode.MAX_LENGTH, message = RequestRules.DIGITS_MAX_MESSAGE)
    private Integer digits;

    @Positive(message = RequestRules.DURATION_POSITIVE_MESSAGE)
    @Max(value = RequestRules.MAX_DURATION_SECONDS, message = RequestRules.DURATION_MAX_MESSAGE)
    private Integer durationSeconds;

    @Size(max = RequestRules.MAX_MESSAGE_LENGTH, message = RequestRules.MESSAGE_LENGTH_MESSAGE)
    @Pattern(regexp = RequestRules.MESSAGE_PATTERN, message = RequestRules.MESSAGE_PATTERN_MESSAGE)
    private String message;
}
