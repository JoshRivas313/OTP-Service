package com.otpservice.otp.adapter.in.http;

import com.otpservice.otp.domain.valueobject.TwilioCredentials;
import com.otpservice.otp.adapter.in.http.dto.request.TwilioConnectRequest;
import com.otpservice.otp.adapter.in.http.dto.response.TwilioStatusResponse;
import com.otpservice.otp.shared.exception.ErrorCode;
import com.otpservice.otp.shared.exception.OtpException;
import com.otpservice.otp.adapter.out.sms.twilio.TwilioSessionService;
import com.otpservice.otp.adapter.out.sms.twilio.TwilioVerifyService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Conexión Twilio", description = "Conecta, desconecta y consulta el estado de la cuenta de Twilio en la sesión")
@RestController
@RequestMapping("/api/twilio")
@RequiredArgsConstructor
public class TwilioConnectHttpAdapter {

  private static final int SESSION_TIMEOUT_SECONDS = 15 * 60;

  private final TwilioSessionService sessionService;
  private final TwilioVerifyService verifyService;

  @PostMapping("/connect")
  public ResponseEntity<TwilioStatusResponse> connect(
    @Valid @RequestBody TwilioConnectRequest request,
    HttpSession session
  ) {
    TwilioCredentials credentials = toCredentials(request);
    verifyService.validateCredentials(credentials);
    sessionService.connect(session, credentials);
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
      return new TwilioCredentials(request.getAccountSid(), request.getAuthToken(),
        request.getVerifyServiceSid(), request.getPhoneNumber());
    } catch (IllegalArgumentException exception) {
      throw new OtpException(ErrorCode.TWILIO_CREDENTIALS_INVALID, exception.getMessage());
    }
  }
}
