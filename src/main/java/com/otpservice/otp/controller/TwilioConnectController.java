package com.otpservice.otp.controller;

import com.otpservice.otp.dto.request.TwilioConnectRequest;
import com.otpservice.otp.dto.response.TwilioStatusResponse;
import com.otpservice.otp.dto.valueobject.TwilioCredentials;
import com.otpservice.otp.exception.ErrorCode;
import com.otpservice.otp.exception.OtpException;
import com.otpservice.otp.sms.TwilioSessionService;
import com.otpservice.otp.sms.TwilioVerifyService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Solo existe cuando twilio-connect.enabled=true: si esta apagado, estas
// rutas ni se registran (404), no hace falta chequear el flag a mano en
// cada metodo.
@RestController
@RequestMapping("/api/twilio")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "twilio-connect", name = "enabled", havingValue = "true")
public class TwilioConnectController {

    private final TwilioSessionService sessionService;
    private final TwilioVerifyService verifyService;

    private static final int SESSION_TIMEOUT_SECONDS = 15 * 60;

    @PostMapping("/connect")
    public ResponseEntity<TwilioStatusResponse> connect(@Valid @RequestBody TwilioConnectRequest request,
                                                          HttpSession session) {
        TwilioCredentials credentials = toCredentials(request);
        verifyService.validateCredentials(credentials);
        sessionService.connect(session, credentials);
        // sesion corta a proposito: es una credencial ajena, no queremos que
        // quede conectada indefinidamente si alguien se olvida de desconectar
        session.setMaxInactiveInterval(SESSION_TIMEOUT_SECONDS);
        return ResponseEntity.ok(TwilioStatusResponse.connected(credentials.masked()));
    }

    @PostMapping("/disconnect")
    public ResponseEntity<TwilioStatusResponse> disconnect(HttpSession session) {
        sessionService.disconnect(session);
        return ResponseEntity.ok(TwilioStatusResponse.disconnected());
    }

    @GetMapping("/status")
    public ResponseEntity<TwilioStatusResponse> status(HttpSession session) {
        return ResponseEntity.ok(sessionService.get(session)
                .map(credentials -> TwilioStatusResponse.connected(credentials.masked()))
                .orElseGet(TwilioStatusResponse::disconnected));
    }

    private TwilioCredentials toCredentials(TwilioConnectRequest request) {
        try {
            return new TwilioCredentials(request.getAccountSid(), request.getAuthToken(), request.getVerifyServiceSid());
        } catch (IllegalArgumentException exception) {
            throw new OtpException(ErrorCode.TWILIO_CREDENTIALS_INVALID, exception.getMessage());
        }
    }
}
