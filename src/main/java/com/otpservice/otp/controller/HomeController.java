package com.otpservice.otp.controller;

import com.otpservice.otp.config.TwilioConnectProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.net.URI;

// Con el flag de Twilio prendido, el onboarding arranca por ahi: conectar
// una cuenta primero, y recien despues llegar al OTP Service. Apagado,
// la raiz manda derecho a la demo, como siempre.
@Controller
@RequiredArgsConstructor
public class HomeController {

    private final TwilioConnectProperties twilioConnectProperties;

    @GetMapping("/")
    public ResponseEntity<Void> home() {
        String target = twilioConnectProperties.enabled() ? "/twilio.html" : "/index.html";
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(target)).build();
    }
}
