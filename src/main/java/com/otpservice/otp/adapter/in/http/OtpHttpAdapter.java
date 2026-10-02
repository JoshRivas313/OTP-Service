package com.otpservice.otp.adapter.in.http;

import com.otpservice.otp.adapter.config.SendRateLimiter;
import com.otpservice.otp.adapter.config.VerifyRateLimiter;
import com.otpservice.otp.application.dto.GenerateOtpResult;
import com.otpservice.otp.application.dto.VerifyOtpResult;
import com.otpservice.otp.application.port.in.GenerateOtpUseCase;
import com.otpservice.otp.application.port.in.VerifyOtpUseCase;
import com.otpservice.otp.application.port.out.SmsSender;
import com.otpservice.otp.adapter.in.http.dto.request.OtpGenerateRequest;
import com.otpservice.otp.adapter.in.http.dto.request.OtpVerifyRequest;
import com.otpservice.otp.adapter.in.http.dto.response.OtpGenerateResponse;
import com.otpservice.otp.adapter.in.http.dto.response.OtpVerifyResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "OTP local", description = "Genera y verifica códigos con el backend propio (HMAC-SHA256)")
@RestController
@ConditionalOnProperty(name = "otp.local-api-enabled", havingValue = "true", matchIfMissing = true)
@RequestMapping("/otps")
@RequiredArgsConstructor
public class OtpHttpAdapter {

  private final GenerateOtpUseCase generateUseCase;
  private final VerifyOtpUseCase verifyUseCase;
  private final SmsSender smsSender;
  private final SendRateLimiter rateLimiter;
  private final VerifyRateLimiter verifyRateLimiter;

  @PostMapping
  public ResponseEntity<OtpGenerateResponse> generateOtp(
    @Valid @RequestBody OtpGenerateRequest request,
    HttpServletRequest http
  ) {
    rateLimiter.check(request.getCellphone().getValue(), http.getRemoteAddr());
    var command = new GenerateOtpUseCase.GenerateOtpCommand(
      request.getCellphone(),
      request.getType(),
      request.getPurpose(),
      request.getDigits(),
      request.getDurationSeconds(),
      null
    );
    GenerateOtpResult result = generateUseCase.generate(
      command,
      (destination, message) -> smsSender.send(request.getCellphone(), message)
    );
    return ResponseEntity.status(HttpStatus.CREATED).body(OtpGenerateResponse.from(result));
  }

  @PostMapping("/verify")
  public ResponseEntity<OtpVerifyResponse> verifyOtp(
    @Valid @RequestBody OtpVerifyRequest request,
    HttpServletRequest http
  ) {
    verifyRateLimiter.check(request.getCellphone().getValue(), http.getRemoteAddr());
    var command = new VerifyOtpUseCase.VerifyOtpCommand(
      request.getCellphone(),
      request.getType(),
      request.getPurpose(),
      request.getCode()
    );
    VerifyOtpResult result = verifyUseCase.verify(command);
    return ResponseEntity.ok(OtpVerifyResponse.from(result));
  }
}
