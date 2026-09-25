package com.otpservice.otp.document;

import com.otpservice.otp.domain.valueobject.Cellphone;
import com.otpservice.otp.domain.valueobject.ValidityWindow;
import com.otpservice.otp.domain.valueobject.VerificationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString
@Document(collection = "otps")
@CompoundIndex(name = "otp_cellphone_generated_idx",
        def = "{'cellphone': 1, 'validityWindow.generatedAt': -1}")
public class OtpDocument {

    @Id
    private String id;

    private String cellphone;

    @ToString.Exclude
    private String codeHash;

    private int digits;

    private ValidityWindow validityWindow;

    private VerificationStatus verificationStatus;

    @Indexed(name = "otp_purge_ttl_idx", expireAfterSeconds = 0)
    private Instant purgeAt;

    public record IssueRequest(Cellphone cellphone, String codeHash, int digits,
                                ValidityWindow validityWindow, Instant purgeAt) {
    }

    public static OtpDocument issue(IssueRequest request) {
        return OtpDocument.builder()
                .cellphone(request.cellphone().getValue())
                .codeHash(request.codeHash())
                .digits(request.digits())
                .validityWindow(request.validityWindow())
                .verificationStatus(new VerificationStatus())
                .purgeAt(request.purgeAt())
                .build();
    }

    public boolean isExpired(Instant now) {
        return validityWindow.isExpired(now);
    }

    public boolean isUsed() {
        return verificationStatus.isUsed();
    }

    public boolean isInvalidated() {
        return verificationStatus.isInvalidated();
    }

    public boolean isBlocked(int maxAllowedAttempts) {
        return verificationStatus.isBlocked(maxAllowedAttempts);
    }

    public int getAttempts() {
        return verificationStatus.getAttempts();
    }
}
