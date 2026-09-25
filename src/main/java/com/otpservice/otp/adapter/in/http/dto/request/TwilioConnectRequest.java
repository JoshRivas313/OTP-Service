package com.otpservice.otp.adapter.in.http.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class TwilioConnectRequest {

    @NotBlank(message = "El Account SID es obligatorio")
    private String accountSid;

    @NotBlank(message = "El Auth Token es obligatorio")
    private String authToken;

    @NotBlank(message = "El Verify Service SID es obligatorio")
    private String verifyServiceSid;

    @NotBlank(message = "El número de Twilio es obligatorio")
    private String phoneNumber;
}
