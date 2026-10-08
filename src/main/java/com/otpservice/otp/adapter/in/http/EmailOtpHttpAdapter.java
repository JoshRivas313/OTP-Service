package com.otpservice.otp.adapter.in.http;

import com.otpservice.otp.adapter.config.SendRateLimiter;
import com.otpservice.otp.adapter.exception.EmailDeliveryFailedException;
import com.otpservice.otp.adapter.config.VerifyRateLimiter;
import com.otpservice.otp.adapter.in.http.dto.request.EmailOtpGenerateRequest;
import com.otpservice.otp.adapter.in.http.dto.request.EmailOtpVerifyRequest;
import com.otpservice.otp.adapter.in.http.dto.response.OtpGenerateResponse;
import com.otpservice.otp.adapter.in.http.dto.response.OtpVerifyResponse;
import com.otpservice.otp.application.dto.GenerateOtpResult;
import com.otpservice.otp.application.dto.VerifyOtpResult;
import com.otpservice.otp.application.port.in.GenerateOtpUseCase;
import com.otpservice.otp.application.port.in.VerifyOtpUseCase;
import com.otpservice.otp.application.port.out.EmailSender;
import com.otpservice.otp.domain.valueobject.OtpCode;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "OTP por correo", description = "Genera y verifica códigos enviados por correo electrónico")
@RestController
@RequestMapping("/api/email/otps")
@RequiredArgsConstructor
public class EmailOtpHttpAdapter {

  private final GenerateOtpUseCase generateUseCase;
  private final VerifyOtpUseCase verifyUseCase;
  private final EmailSender emailSender;
  private final SendRateLimiter rateLimiter;
  private final VerifyRateLimiter verifyRateLimiter;

  @PostMapping
  public ResponseEntity<OtpGenerateResponse> generateOtp(
    @Valid @RequestBody EmailOtpGenerateRequest request,
    HttpServletRequest http
  ) {
    rateLimiter.check(request.getEmail().getValue(), http.getRemoteAddr());
    var command = new GenerateOtpUseCase.GenerateOtpCommand(
      request.getEmail(),
      request.getType(),
      request.getPurpose(),
      request.getDigits(),
      request.getDurationSeconds(),
      request.getMessage()
    );
    GenerateOtpResult result;
    try {
      result = generateUseCase.generate(
        command,
        (destination, message) -> emailSender.send(request.getEmail(), message)
      );
    } catch (EmailDeliveryFailedException exception) {
      rateLimiter.refund(request.getEmail().getValue(), http.getRemoteAddr());
      throw exception;
    }
    return ResponseEntity.status(HttpStatus.CREATED).body(OtpGenerateResponse.from(result));
  }

  @PostMapping("/verify")
  public ResponseEntity<OtpVerifyResponse> verifyOtp(
    @Valid @RequestBody EmailOtpVerifyRequest request,
    HttpServletRequest http
  ) {
    verifyRateLimiter.check(request.getEmail().getValue(), http.getRemoteAddr());
    var command = new VerifyOtpUseCase.VerifyOtpCommand(
      request.getEmail(), request.getType(), request.getPurpose(), OtpCode.parse(request.getCode()));
    VerifyOtpResult result = verifyUseCase.verify(command);
    return ResponseEntity.ok(OtpVerifyResponse.from(result));
  }
}
