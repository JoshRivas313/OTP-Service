package com.otpservice.otp.adapter.out.persistence.document;

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
@CompoundIndex(name = "otp_destination_generated_idx",
        def = "{'destination': 1, 'validityWindow.generatedAt': -1}")
public class OtpDocument {

    @Id
    private String id;

    private String destination;

    @ToString.Exclude
    private String codeHash;

    private int digits;

    private ValidityWindow validityWindow;

    private VerificationStatus verificationStatus;

    @Indexed(name = "otp_purge_ttl_idx", expireAfterSeconds = 0)
    private Instant purgeAt;
}
