package com.otpservice.otp.adapter.out.persistence.mapper;

import com.otpservice.otp.adapter.out.persistence.document.OtpDocument;
import com.otpservice.otp.domain.model.Otp;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;

@Component
@Profile("mongo")
public class OtpPersistenceMapper {

    public OtpDocument toDocument(Otp otp) {
        return OtpDocument.builder()
                .id(otp.getId())
                .destination(otp.getDestination())
                .purpose(otp.getPurpose())
                .codeHash(otp.getCodeHash())
                .digits(otp.getDigits())
                .validityWindow(otp.getValidityWindow())
                .verificationStatus(otp.getVerificationStatus())
                .purgeAt(otp.getPurgeAt())
                .build();
    }

    public Otp toDomain(OtpDocument document) {
        return Otp.builder()
                .id(document.getId())
                .destination(document.getDestination())
                .purpose(document.getPurpose())
                .codeHash(document.getCodeHash())
                .digits(document.getDigits())
                .validityWindow(document.getValidityWindow())
                .verificationStatus(document.getVerificationStatus())
                .purgeAt(document.getPurgeAt())
                .build();
    }
}
