package com.otpservice.otp.controller;

import com.otpservice.otp.config.PublicConfigResponse;
import com.otpservice.otp.config.TwilioConnectProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// Siempre disponible (a diferencia de TwilioConnectController): la UI la usa
// para decidir si muestra la tarjeta de "conectar tu cuenta de Twilio".
@RestController
@RequiredArgsConstructor
public class ConfigController {

    private final TwilioConnectProperties twilioConnectProperties;

    @GetMapping("/api/config")
    public PublicConfigResponse config() {
        return new PublicConfigResponse(twilioConnectProperties.enabled());
    }
}
