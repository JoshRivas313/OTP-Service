package com.otpservice.otp.adapter.in.http;

import com.otpservice.otp.domain.port.input.GenerateOtpUseCase;
import com.otpservice.otp.domain.port.input.VerifyOtpUseCase;
import com.otpservice.otp.dto.request.OtpGenerateRequest;
import com.otpservice.otp.dto.request.OtpVerifyRequest;
import com.otpservice.otp.dto.response.OtpGenerateResponse;
import com.otpservice.otp.dto.response.OtpVerifyResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/otps")
@Tag(name = "OTP Local", description = "OTP generation and verification")
@RequiredArgsConstructor
public class OtpHttpAdapter {
  private final GenerateOtpUseCase generateUseCase;
  private final VerifyOtpUseCase verifyUseCase;

  @PostMapping
  public ResponseEntity<OtpGenerateResponse> generate(@RequestBody OtpGenerateRequest request) {
    var command = new GenerateOtpUseCase.GenerateOtpCommand(
      request.getCellphone(),
      request.getDigits(),
      request.getDurationSeconds()
    );
    generateUseCase.generate(command);
    return ResponseEntity.ok(new OtpGenerateResponse("Código generado", "OTP_GENERATED"));
  }

  @PostMapping("/verify")
  public ResponseEntity<OtpVerifyResponse> verify(@RequestBody OtpVerifyRequest request) {
    var command = new VerifyOtpUseCase.VerifyOtpCommand(
      request.getCellphone(),
      request.getCode()
    );
    verifyUseCase.verify(command);
    return ResponseEntity.ok(new OtpVerifyResponse("Código verificado", "OTP_VERIFIED"));
  }
}
