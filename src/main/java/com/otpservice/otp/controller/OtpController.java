package com.otpservice.otp.controller;

import com.otpservice.otp.dto.request.OtpGenerateRequest;
import com.otpservice.otp.dto.request.OtpVerifyRequest;
import com.otpservice.otp.dto.response.OtpGenerateResponse;
import com.otpservice.otp.dto.response.OtpVerifyResponse;
import com.otpservice.otp.service.OtpService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/otps")
@RequiredArgsConstructor
public class OtpController {

    private final OtpService otpService;

    @PostMapping
    public ResponseEntity<OtpGenerateResponse> generateOtp(@Valid @RequestBody OtpGenerateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(otpService
                        .generateOtp(request)
                );
    }

    @PostMapping("/verify")
    public ResponseEntity<OtpVerifyResponse> verifyOtp(@Valid @RequestBody OtpVerifyRequest request) {
        return ResponseEntity
                .ok(otpService
                        .verifyOtp(request)
                );
    }
}
