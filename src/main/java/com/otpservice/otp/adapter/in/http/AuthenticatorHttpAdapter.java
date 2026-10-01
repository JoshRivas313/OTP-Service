package com.otpservice.otp.adapter.in.http;

import com.otpservice.otp.adapter.in.http.dto.request.AuthenticatorCodeRequest;
import com.otpservice.otp.adapter.in.http.dto.request.EnrollAuthenticatorRequest;
import com.otpservice.otp.adapter.in.http.dto.response.AuthenticatorVerifyResponse;
import com.otpservice.otp.adapter.in.http.dto.response.EnrollmentResponse;
import com.otpservice.otp.application.port.in.AuthenticatorCodeCommand;
import com.otpservice.otp.application.port.in.ConfirmEnrollmentUseCase;
import com.otpservice.otp.application.port.in.EnrollAuthenticatorUseCase;
import com.otpservice.otp.application.port.in.RemoveEnrollmentUseCase;
import com.otpservice.otp.application.port.in.VerifyAuthenticatorCodeUseCase;
import com.otpservice.otp.domain.valueobject.HmacType;
import com.otpservice.otp.domain.valueobject.EmailAddress;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "App autenticadora", description = "HOTP y TOTP: el código lo genera la app del usuario y el servidor lo recalcula")
@RestController
@RequestMapping("/api/authenticator")
@RequiredArgsConstructor
public class AuthenticatorHttpAdapter {

  private final EnrollAuthenticatorUseCase enrollUseCase;
  private final ConfirmEnrollmentUseCase confirmUseCase;
  private final VerifyAuthenticatorCodeUseCase verifyUseCase;
  private final RemoveEnrollmentUseCase removeUseCase;
  private final EmailVerificationSession emailVerification;

  // no-store: la respuesta lleva el secreto en claro.
  @PostMapping("/enrollments")
  public ResponseEntity<EnrollmentResponse> enroll(@Valid @RequestBody EnrollAuthenticatorRequest request,
                                                   HttpSession session) {
    emailVerification.requireVerified(session, request.getEmail());
    var command = new EnrollAuthenticatorUseCase.EnrollCommand(
      request.getEmail(), request.getType(), request.getDigits(), request.getPeriodSeconds());
    return ResponseEntity.status(HttpStatus.CREATED)
      .cacheControl(CacheControl.noStore())
      .body(EnrollmentResponse.from(enrollUseCase.enroll(command)));
  }

  @PostMapping("/enrollments/confirm")
  public ResponseEntity<AuthenticatorVerifyResponse> confirm(@Valid @RequestBody AuthenticatorCodeRequest request) {
    return ResponseEntity.ok(AuthenticatorVerifyResponse.from(confirmUseCase.confirm(toCommand(request))));
  }

  @PostMapping("/verify")
  public ResponseEntity<AuthenticatorVerifyResponse> verify(@Valid @RequestBody AuthenticatorCodeRequest request) {
    return ResponseEntity.ok(AuthenticatorVerifyResponse.from(verifyUseCase.verify(toCommand(request))));
  }

  @DeleteMapping("/enrollments")
  public ResponseEntity<Void> remove(@RequestParam EmailAddress email, @RequestParam HmacType type,
                                     HttpSession session) {
    emailVerification.requireVerified(session, email);
    removeUseCase.remove(email, type);
    return ResponseEntity.noContent().build();
  }

  private static AuthenticatorCodeCommand toCommand(AuthenticatorCodeRequest request) {
    return new AuthenticatorCodeCommand(request.getEmail(), request.getType(), request.getCode());
  }
}
