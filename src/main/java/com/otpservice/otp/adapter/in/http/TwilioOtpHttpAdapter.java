package com.otpservice.otp.adapter.in.http;

import com.otpservice.otp.adapter.exception.DestinationNotVerifiedException;
import com.otpservice.otp.adapter.exception.TwilioNotConnectedException;
import com.otpservice.otp.adapter.in.http.dto.request.TwilioOtpGenerateRequest;
import com.otpservice.otp.adapter.in.http.dto.request.TwilioOtpVerifyRequest;
import com.otpservice.otp.adapter.in.http.dto.response.OtpGenerateResponse;
import com.otpservice.otp.adapter.in.http.dto.response.OtpVerifyResponse;
import com.otpservice.otp.adapter.out.sms.twilio.TwilioSessionService;
import com.otpservice.otp.adapter.out.sms.twilio.TwilioSessionSmsSender;
import com.otpservice.otp.application.dto.GenerateOtpResult;
import com.otpservice.otp.application.dto.VerifyOtpResult;
import com.otpservice.otp.application.port.in.GenerateOtpUseCase;
import com.otpservice.otp.application.port.in.VerifyOtpUseCase;
import com.otpservice.otp.domain.exception.InvalidOtpException;
import com.otpservice.otp.domain.valueobject.OtpCode;
import com.otpservice.otp.domain.valueobject.TwilioCredentials;
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
  private final TwilioSessionSmsSender sessionSmsSender;
  private final GenerateOtpUseCase generateUseCase;
  private final VerifyOtpUseCase verifyUseCase;

  @PostMapping
  public ResponseEntity<OtpGenerateResponse> generateOtp(
    @Valid @RequestBody TwilioOtpGenerateRequest request,
    HttpSession session
  ) {
    TwilioCredentials credentials = requireConnected(session);
    if (!sessionService.accountInfo(session).allows(request.getCellphone())) {
      throw new DestinationNotVerifiedException();
    }
    var command = new GenerateOtpUseCase.GenerateOtpCommand(
      request.getCellphone(),
      request.getDigits(),
      request.getDurationSeconds(),
      request.getMessage()
    );
    GenerateOtpResult result = generateUseCase.generate(
      command,
      (destination, message) -> sessionSmsSender.send(credentials, destination, message)
    );
    return ResponseEntity.status(HttpStatus.CREATED).body(
      new OtpGenerateResponse(result.success(), result.message(), result.demoCode())
    );
  }

  @PostMapping("/verify")
  public ResponseEntity<OtpVerifyResponse> verifyOtp(
    @Valid @RequestBody TwilioOtpVerifyRequest request,
    HttpSession session
  ) {
    requireConnected(session);
    OtpCode code = toOtpCode(request.getCode());
    var command = new VerifyOtpUseCase.VerifyOtpCommand(request.getCellphone(), code);
    VerifyOtpResult result = verifyUseCase.verify(command);
    return ResponseEntity.ok(new OtpVerifyResponse(result.success(), result.message()));
  }

  private TwilioCredentials requireConnected(HttpSession session) {
    return sessionService.get(session)
      .orElseThrow(TwilioNotConnectedException::new);
  }

  private OtpCode toOtpCode(String code) {
    try {
      return new OtpCode(code);
    } catch (IllegalArgumentException exception) {
      throw new InvalidOtpException();
    }
  }
}
