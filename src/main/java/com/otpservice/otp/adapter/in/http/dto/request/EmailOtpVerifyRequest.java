package com.otpservice.otp.adapter.in.http.dto.request;

import com.otpservice.otp.domain.valueobject.OtpProtocol;
import com.otpservice.otp.domain.valueobject.Purpose;
import com.otpservice.otp.domain.valueobject.EmailAddress;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class EmailOtpVerifyRequest {

    @NotNull(message = "El correo es obligatorio")
    private EmailAddress email;

    private OtpProtocol type;

    private Purpose purpose;

    @NotBlank(message = "El código es obligatorio")
    private String code;
}
