package com.otpservice.otp.adapter.in.http;

import com.otpservice.otp.application.config.HmacSettings;
import com.otpservice.otp.application.config.OtpSettings;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// Las reglas que aplica la verificacion, para que la interfaz las explique con los mismos valores.
@Tag(name = "Política", description = "Reglas de verificación vigentes")
@RestController
@RequiredArgsConstructor
public class OtpPolicyHttpAdapter {

  private final HmacSettings hmacSettings;
  private final OtpSettings otpSettings;

  @GetMapping("/api/otp-policy")
  public OtpPolicyResponse policy() {
    return new OtpPolicyResponse(hmacSettings.totpToleranceSteps(), hmacSettings.hotpLookAhead(),
        otpSettings.maxAttempts(), otpSettings.lockSeconds());
  }

  public record OtpPolicyResponse(int totpToleranceSteps, int hotpLookAhead, int maxAttempts, int lockSeconds) {
  }
}
