package com.otpservice.otp.controller;

import com.otpservice.otp.dto.request.TwilioOtpGenerateRequest;
import com.otpservice.otp.dto.request.TwilioOtpVerifyRequest;
import com.otpservice.otp.dto.response.OtpGenerateResponse;
import com.otpservice.otp.dto.response.OtpVerifyResponse;
import com.otpservice.otp.dto.valueobject.TwilioCredentials;
import com.otpservice.otp.exception.ErrorCode;
import com.otpservice.otp.exception.OtpException;
import com.otpservice.otp.service.TwilioOtpService;
import com.otpservice.otp.sms.TwilioSessionService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Analogo a OtpController pero exigiendo sesion Twilio conectada: el OTP se
// genera y guarda igual que en /otps (Mongo+HMAC), solo cambia por donde sale
// el SMS (la cuenta que el visitante conecto, no la del servidor).
@RestController
@RequestMapping("/api/twilio/otps")
@RequiredArgsConstructor
public class TwilioOtpController {

    private final TwilioSessionService sessionService;
    private final TwilioOtpService twilioOtpService;

    @PostMapping
    public ResponseEntity<OtpGenerateResponse> generateOtp(@Valid @RequestBody TwilioOtpGenerateRequest request,
                                                             HttpSession session) {
        TwilioCredentials credentials = requireConnected(session);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(twilioOtpService.generateOtp(credentials, request.getCellphone(), request.getDigits(), request.getDurationSeconds()));
    }

    @PostMapping("/verify")
    public ResponseEntity<OtpVerifyResponse> verifyOtp(@Valid @RequestBody TwilioOtpVerifyRequest request,
                                                         HttpSession session) {
        requireConnected(session);
        return ResponseEntity.ok(twilioOtpService.verifyOtp(request.getCellphone(), request.getCode()));
    }

    private TwilioCredentials requireConnected(HttpSession session) {
        return sessionService.get(session)
                .orElseThrow(() -> new OtpException(ErrorCode.TWILIO_NOT_CONNECTED));
    }

}
