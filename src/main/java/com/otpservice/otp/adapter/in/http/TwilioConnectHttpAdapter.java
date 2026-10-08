package com.otpservice.otp.adapter.in.http;

import com.otpservice.otp.adapter.exception.TwilioCredentialsInvalidException;
import com.otpservice.otp.domain.valueobject.TwilioCredentials;
import com.otpservice.otp.adapter.config.ConnectRateLimiter;
import com.otpservice.otp.adapter.in.http.dto.request.TwilioConnectRequest;
import com.otpservice.otp.adapter.in.http.dto.response.TwilioStatusResponse;
import com.otpservice.otp.adapter.out.sms.twilio.TwilioAccountInfo;
import com.otpservice.otp.adapter.out.sms.twilio.TwilioSessionService;
import com.otpservice.otp.adapter.out.sms.twilio.TwilioVerifyService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import java.util.Comparator;
import java.util.List;
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
  private final ConnectRateLimiter connectRateLimiter;

  @PostMapping("/connect")
  public ResponseEntity<TwilioStatusResponse> connect(
    @Valid @RequestBody TwilioConnectRequest request,
    HttpServletRequest http
  ) {
    connectRateLimiter.check(http.getRemoteAddr());
    TwilioCredentials credentials = toCredentials(request);
    verifyService.validateCredentials(credentials);
    TwilioAccountInfo accountInfo = verifyService.fetchAccountInfo(credentials);
    // La sesion nace aqui, ya con credenciales validas, y cambia de ID para no heredar una sesion previa.
    HttpSession session = http.getSession(true);
    http.changeSessionId();
    sessionService.connect(session, credentials, accountInfo);
    session.setMaxInactiveInterval(SESSION_TIMEOUT_SECONDS);
    return ResponseEntity.ok(toStatus(credentials, accountInfo));
  }

  @PostMapping("/disconnect")
  public ResponseEntity<TwilioStatusResponse> disconnect(HttpServletRequest http) {
    HttpSession session = http.getSession(false);
    sessionService.disconnect(session);
    if (session != null) {
      session.invalidate();
    }
    return ResponseEntity.ok(TwilioStatusResponse.disconnected());
  }

  @GetMapping("/status")
  public ResponseEntity<TwilioStatusResponse> status(HttpServletRequest http) {
    HttpSession session = http.getSession(false);
    return ResponseEntity.ok(sessionService.get(session)
      .map(credentials -> toStatus(credentials, sessionService.accountInfo(session)))
      .orElseGet(TwilioStatusResponse::disconnected));
  }

  private TwilioStatusResponse toStatus(TwilioCredentials credentials, TwilioAccountInfo accountInfo) {
    List<String> maskedNumbers = accountInfo.verifiedNumbers().stream()
      .sorted(Comparator.naturalOrder())
      .map(TwilioConnectHttpAdapter::mask)
      .toList();
    return TwilioStatusResponse.connected(credentials.masked(), accountInfo.restricted(), maskedNumbers);
  }

  private static String mask(String number) {
    int hidden = Math.max(number.length() - 3, 0);
    return "*".repeat(hidden) + number.substring(hidden);
  }

  private TwilioCredentials toCredentials(TwilioConnectRequest request) {
    try {
      return new TwilioCredentials(request.getAccountSid(), request.getAuthToken(),
        request.getVerifyServiceSid(), request.getPhoneNumber());
    } catch (IllegalArgumentException exception) {
      throw new TwilioCredentialsInvalidException(exception.getMessage());
    }
  }
}
