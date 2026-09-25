package com.otpservice.otp.adapter.config;

import com.otpservice.otp.domain.port.input.GenerateOtpUseCase;
import com.otpservice.otp.domain.port.input.VerifyOtpUseCase;
import com.otpservice.otp.domain.port.output.OtpPersistencePort;
import com.otpservice.otp.domain.port.output.SmsPort;
import com.otpservice.otp.service.OtpService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Integration adapter for gradual migration from legacy to hexagonal architecture.
 *
 * This configuration bridges the old OtpService (legacy) with new architecture ports.
 * As new implementations are created, this adapter can be removed.
 *
 * Migration roadmap:
 * 1. ✅ Created hexagonal ports and domain layer
 * 2. ✅ Created application use cases
 * 3. ⏳ Implement all adapters
 * 4. ⏳ Switch controllers to new adapters
 * 5. ⏳ Remove legacy services
 */
@Configuration
public class LegacyIntegrationAdapter {

  /**
   * Falls back to legacy OtpService if new implementations not available.
   * Once new implementations are ready, this bean will be @ConditionalOnMissingBean
   */
  @Bean
  @ConditionalOnMissingBean(GenerateOtpUseCase.class)
  public GenerateOtpUseCase generateOtpUseCaseLegacy(OtpService service) {
    return command -> {
      service.generateOtp(new com.otpservice.otp.dto.request.OtpGenerateRequest(
        command.cellphone(),
        command.digits(),
        command.durationSeconds()
      ));
      return command;
    };
  }

  @Bean
  @ConditionalOnMissingBean(VerifyOtpUseCase.class)
  public VerifyOtpUseCase verifyOtpUseCaseLegacy(OtpService service) {
    return command -> {
      service.verifyOtp(new com.otpservice.otp.dto.request.OtpVerifyRequest(
        command.cellphone(),
        command.code()
      ));
      return new VerifyOtpUseCase.VerifyOtpResult("Verificado", "OTP_VERIFIED");
    };
  }
}
