package com.otpservice.otp.adapter.in.http;

import com.otpservice.otp.domain.port.input.TwilioGenerateOtpUseCase;
import com.otpservice.otp.domain.port.input.TwilioVerifyOtpUseCase;
import com.otpservice.otp.dto.request.TwilioOtpGenerateRequest;
import com.otpservice.otp.dto.request.TwilioOtpVerifyRequest;
import com.otpservice.otp.dto.response.OtpGenerateResponse;
import com.otpservice.otp.dto.response.OtpVerifyResponse;
import com.otpservice.otp.sms.twilioconnect.TwilioSessionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/twilio/otps")
@Tag(name = "Twilio OTP", description = "OTP generation and verification via Twilio Verify API")
@RequiredArgsConstructor
public class TwilioOtpHttpAdapter {
  private final TwilioGenerateOtpUseCase generateUseCase;
  private final TwilioVerifyOtpUseCase verifyUseCase;
  private final TwilioSessionService sessionService;

  @PostMapping
  public ResponseEntity<OtpGenerateResponse> generate(
    @RequestBody TwilioOtpGenerateRequest request,
    HttpSession session
  ) {
    var credentials = sessionService.getTwilioCredentials(session);

    var command = new TwilioGenerateOtpUseCase.TwilioGenerateOtpCommand(
      request.getCellphone(),
      request.getDigits(),
      request.getDurationSeconds(),
      credentials.getAccountSid(),
      credentials.getAuthToken(),
      credentials.getVerifyServiceSid(),
      credentials.getPhoneNumber()
    );

    var result = generateUseCase.generate(command);
    return ResponseEntity.ok(new OtpGenerateResponse(result.message(), result.code()));
  }

  @PostMapping("/verify")
  public ResponseEntity<OtpVerifyResponse> verify(
    @RequestBody TwilioOtpVerifyRequest request,
    HttpSession session
  ) {
    var credentials = sessionService.getTwilioCredentials(session);

    var command = new TwilioVerifyOtpUseCase.TwilioVerifyOtpCommand(
      request.getCellphone(),
      request.getCode(),
      credentials.getAccountSid(),
      credentials.getAuthToken(),
      credentials.getVerifyServiceSid()
    );

    var result = verifyUseCase.verify(command);
    return ResponseEntity.ok(new OtpVerifyResponse(result.message(), result.code()));
  }
}
