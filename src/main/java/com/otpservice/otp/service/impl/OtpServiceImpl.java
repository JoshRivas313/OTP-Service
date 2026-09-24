package com.otpservice.otp.service.impl;

import com.otpservice.otp.config.OtpProperties;
import com.otpservice.otp.document.OtpDocument;
import com.otpservice.otp.dto.request.OtpGenerateRequest;
import com.otpservice.otp.dto.request.OtpVerifyRequest;
import com.otpservice.otp.dto.response.OtpGenerateResponse;
import com.otpservice.otp.dto.response.OtpVerifyResponse;
import com.otpservice.otp.dto.valueobject.Cellphone;
import com.otpservice.otp.dto.valueobject.OtpCode;
import com.otpservice.otp.dto.valueobject.ValidityWindow;
import com.otpservice.otp.exception.ErrorCode;
import com.otpservice.otp.exception.OtpException;
import com.otpservice.otp.repository.OtpRepository;
import com.otpservice.otp.security.CodeHasher;
import com.otpservice.otp.service.OtpService;
import com.otpservice.otp.sms.SmsSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class OtpServiceImpl implements OtpService {

    private final OtpRepository otpRepository;
    private final CodeHasher codeHasher;
    private final SmsSender smsSender;
    private final OtpProperties properties;
    private final Clock clock;

    @Override
    public OtpGenerateResponse generateOtp(OtpGenerateRequest request) {
        Cellphone cellphone = request.getCellphone();
        int digits = request.getDigits() != null ? request.getDigits() : properties.digits();
        int durationSeconds = request.getDurationSeconds() != null
                ? request.getDurationSeconds() : properties.durationSeconds();

        otpRepository.invalidateActive(cellphone.getValue());

        Instant now = clock.instant();
        OtpCode code = OtpCode.generate(digits);
        ValidityWindow window = ValidityWindow.from(now, durationSeconds);
        String codeHash = codeHasher.hash(code.getValue());
        Instant purgeAt = window.getExpiresAt().plusSeconds(properties.retentionSeconds());

        otpRepository.save(OtpDocument.issue(
                new OtpDocument.IssueRequest(cellphone, codeHash, digits, window, purgeAt)));

        smsSender.send(cellphone, properties.messageTemplate().formatted(code.getValue(), durationSeconds));

        return properties.demoMode()
                ? OtpGenerateResponse.sentInDemoMode(code.getValue())
                : OtpGenerateResponse.sent();
    }

    @Override
    public OtpVerifyResponse verifyOtp(OtpVerifyRequest request) {
        Cellphone cellphone = request.getCellphone();
        Instant now = clock.instant();
        int maxAttempts = properties.maxAttempts();

        OtpDocument otp = otpRepository
                .findFirstByCellphoneOrderByValidityWindowGeneratedAtDesc(cellphone.getValue())
                .orElseThrow(() -> new OtpException(ErrorCode.OTP_NOT_FOUND));

        if (otp.isInvalidated()) {
            throw new OtpException(ErrorCode.OTP_INVALIDATED);
        }
        if (otp.isUsed()) {
            throw new OtpException(ErrorCode.OTP_ALREADY_USED);
        }
        if (otp.isExpired(now)) {
            throw new OtpException(ErrorCode.OTP_EXPIRED);
        }
        if (otp.isBlocked(maxAttempts)) {
            throw new OtpException(ErrorCode.OTP_BLOCKED);
        }

        String codeHash = codeHasher.hash(request.getCode().getValue());
        if (otpRepository.claimIfMatches(otp.getId(), codeHash, now, maxAttempts).isPresent()) {
            log.info("OTP verificado cellphone={}", cellphone.masked());
            return OtpVerifyResponse.verified();
        }

        OtpDocument updated = otpRepository.registerFailedAttempt(otp.getId())
                .orElseThrow(() -> new OtpException(ErrorCode.OTP_NOT_FOUND));

        if (updated.isBlocked(maxAttempts)) {
            throw new OtpException(ErrorCode.OTP_BLOCKED);
        }
        throw new OtpException(ErrorCode.OTP_INVALID,
                "El código es incorrecto (intento %d de %d)".formatted(updated.getAttempts(), maxAttempts));
    }
}
