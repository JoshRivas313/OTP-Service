package com.otpservice.otp.adapter.in.http;

import com.otpservice.otp.dto.request.TwilioConnectRequest;
import com.otpservice.otp.dto.response.ErrorResponse;
import com.otpservice.otp.dto.response.TwilioStatusResponse;
import com.otpservice.otp.sms.twilioconnect.TwilioSessionService;
import com.otpservice.otp.sms.twilioconnect.TwilioVerifyService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/twilio")
@Tag(name = "Twilio Connection", description = "Manage Twilio Verify credentials")
@RequiredArgsConstructor
public class TwilioConnectHttpAdapter {
  private final TwilioSessionService sessionService;
  private final TwilioVerifyService verifyService;

  @PostMapping("/connect")
  public ResponseEntity<?> connect(
    @Valid @RequestBody TwilioConnectRequest request,
    HttpSession session
  ) {
    try {
      verifyService.validateCredentials(request);
      sessionService.storeTwilioCredentials(session, request);
      return ResponseEntity.ok(
        new com.otpservice.otp.dto.response.OtpGenerateResponse(
          "Credenciales de Twilio conectadas",
          "TWILIO_CONNECTED"
        )
      );
    } catch (Exception e) {
      return ResponseEntity.status(422)
        .body(new ErrorResponse("Credenciales inválidas", "INVALID_CREDENTIALS"));
    }
  }

  @GetMapping("/status")
  public ResponseEntity<TwilioStatusResponse> status(HttpSession session) {
    var credentials = sessionService.getTwilioCredentials(session);
    boolean connected = credentials != null;
    String masked = connected ? maskCredentials(credentials.getAccountSid()) : "";
    return ResponseEntity.ok(new TwilioStatusResponse(connected, masked));
  }

  @PostMapping("/disconnect")
  public ResponseEntity<com.otpservice.otp.dto.response.OtpGenerateResponse> disconnect(
    HttpSession session
  ) {
    sessionService.clearTwilioCredentials(session);
    return ResponseEntity.ok(
      new com.otpservice.otp.dto.response.OtpGenerateResponse(
        "Desconectado de Twilio",
        "TWILIO_DISCONNECTED"
      )
    );
  }

  private String maskCredentials(String sid) {
    if (sid == null || sid.length() < 8) return sid;
    return sid.substring(0, 4) + "****" + sid.substring(sid.length() - 4);
  }
}
