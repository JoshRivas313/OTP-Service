package com.otpservice.otp.adapter.in.http;

import com.otpservice.otp.domain.port.input.GenerateOtpUseCase;
import com.otpservice.otp.domain.port.input.VerifyOtpUseCase;
import com.otpservice.otp.adapter.in.http.dto.request.OtpGenerateRequest;
import com.otpservice.otp.adapter.in.http.dto.request.OtpVerifyRequest;
import com.otpservice.otp.adapter.in.http.dto.response.OtpGenerateResponse;
import com.otpservice.otp.adapter.in.http.dto.response.OtpVerifyResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "OTP local", description = "Genera y verifica códigos con el backend propio (Mongo + HMAC-SHA256)")
@RestController
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
      request.getDurationSeconds()
    );
    return ResponseEntity.status(HttpStatus.CREATED).body(generateUseCase.generate(command));
  }

  @PostMapping("/verify")
  public ResponseEntity<OtpVerifyResponse> verifyOtp(@Valid @RequestBody OtpVerifyRequest request) {
    var command = new VerifyOtpUseCase.VerifyOtpCommand(
      request.getCellphone(),
      request.getCode()
    );
    return ResponseEntity.ok(verifyUseCase.verify(command));
  }
}
