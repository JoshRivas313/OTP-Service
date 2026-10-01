package com.otpservice.otp.adapter.in.http;

import io.swagger.v3.oas.annotations.Hidden;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Hidden
@RestController
@RequiredArgsConstructor
public class HealthHttpAdapter {

  private final Environment environment;

  @GetMapping("/health")
  public Map<String, String> health() {
    return Map.of(
      "status", "UP",
      "storage", environment.acceptsProfiles(Profiles.of("mongo")) ? "mongo" : "memory");
  }
}
