package com.otpservice.otp.adapter.in.http;

import com.otpservice.otp.adapter.config.AuthenticatorProperties;
import com.otpservice.otp.adapter.exception.EmailNotVerifiedException;
import com.otpservice.otp.domain.valueobject.EmailAddress;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.time.Clock;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class EmailVerificationSession {

  private static final String ATTRIBUTE = "verifiedEmail";

  private final AuthenticatorProperties properties;
  private final Clock clock;

  private record Verified(String email, Instant at) implements Serializable {
  }

  public void markVerified(HttpSession session, EmailAddress email) {
    session.setAttribute(ATTRIBUTE, new Verified(email.getValue(), clock.instant()));
  }

  public void requireVerified(HttpSession session, EmailAddress email) {
    if (!(session.getAttribute(ATTRIBUTE) instanceof Verified verified)
        || !verified.email().equals(email.getValue())
        || verified.at().plusSeconds(properties.emailVerificationMaxAgeSeconds()).isBefore(clock.instant())) {
      throw new EmailNotVerifiedException();
    }
  }
}
