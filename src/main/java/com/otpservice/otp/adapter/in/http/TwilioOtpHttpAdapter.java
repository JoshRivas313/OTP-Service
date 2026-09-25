package com.otpservice.otp.adapter.in.http;

import com.otpservice.otp.domain.port.input.TwilioGenerateOtpUseCase;
import com.otpservice.otp.domain.port.input.TwilioVerifyOtpUseCase;
import com.otpservice.otp.domain.valueobject.TwilioCredentials;
import com.otpservice.otp.adapter.in.http.dto.request.TwilioOtpGenerateRequest;
import com.otpservice.otp.adapter.in.http.dto.request.TwilioOtpVerifyRequest;
import com.otpservice.otp.adapter.in.http.dto.response.OtpGenerateResponse;
import com.otpservice.otp.adapter.in.http.dto.response.OtpVerifyResponse;
import com.otpservice.otp.shared.exception.ErrorCode;
import com.otpservice.otp.shared.exception.OtpException;
import com.otpservice.otp.adapter.out.sms.twilio.TwilioSessionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "OTP con Twilio", description = "Genera y verifica códigos usando la cuenta de Twilio conectada en sesión")
@RestController
@RequestMapping("/api/twilio/otps")
@RequiredArgsConstructor
public class TwilioOtpHttpAdapter {

  private final TwilioSessionService sessionService;
  private final TwilioGenerateOtpUseCase generateUseCase;
  private final TwilioVerifyOtpUseCase verifyUseCase;

  @PostMapping
  public ResponseEntity<OtpGenerateResponse> generateOtp(
    @Valid @RequestBody TwilioOtpGenerateRequest request,
    HttpSession session
  ) {
    TwilioCredentials credentials = requireConnected(session);
    return ResponseEntity.status(HttpStatus.CREATED).body(
      generateUseCase.generate(credentials, request.getCellphone(), request.getDigits(), request.getDurationSeconds())
    );
  }

  @PostMapping("/verify")
  public ResponseEntity<OtpVerifyResponse> verifyOtp(
    @Valid @RequestBody TwilioOtpVerifyRequest request,
    HttpSession session
  ) {
    requireConnected(session);
    return ResponseEntity.ok(verifyUseCase.verify(request.getCellphone(), request.getCode()));
  }

  private TwilioCredentials requireConnected(HttpSession session) {
    return sessionService.get(session)
      .orElseThrow(() -> new OtpException(ErrorCode.TWILIO_NOT_CONNECTED));
  }
}
