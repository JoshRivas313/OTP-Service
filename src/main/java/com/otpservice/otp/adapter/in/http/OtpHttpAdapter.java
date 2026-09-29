package com.otpservice.otp.adapter.in.http;

import com.otpservice.otp.application.dto.GenerateOtpResult;
import com.otpservice.otp.application.dto.VerifyOtpResult;
import com.otpservice.otp.application.port.in.GenerateOtpUseCase;
import com.otpservice.otp.application.port.in.VerifyOtpUseCase;
import com.otpservice.otp.adapter.in.http.dto.request.OtpGenerateRequest;
import com.otpservice.otp.adapter.in.http.dto.request.OtpVerifyRequest;
import com.otpservice.otp.adapter.in.http.dto.response.OtpGenerateResponse;
import com.otpservice.otp.adapter.in.http.dto.response.OtpVerifyResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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

  @PostMapping
  public ResponseEntity<OtpGenerateResponse> generateOtp(@Valid @RequestBody OtpGenerateRequest request) {
    var command = new GenerateOtpUseCase.GenerateOtpCommand(
      request.getCellphone(),
      request.getDigits(),
      request.getDurationSeconds(),
      null
    );
    GenerateOtpResult result = generateUseCase.generate(command);
    return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(result));
  }

  @PostMapping("/verify")
  public ResponseEntity<OtpVerifyResponse> verifyOtp(@Valid @RequestBody OtpVerifyRequest request) {
    var command = new VerifyOtpUseCase.VerifyOtpCommand(
      request.getCellphone(),
      request.getCode()
    );
    VerifyOtpResult result = verifyUseCase.verify(command);
    return ResponseEntity.ok(new OtpVerifyResponse(result.success(), result.message()));
  }

  private OtpGenerateResponse toResponse(GenerateOtpResult result) {
    return new OtpGenerateResponse(result.success(), result.message(), result.demoCode());
  }
}
