package com.otpservice.otp.adapter.out.persistence.document;

import com.otpservice.otp.domain.valueobject.CredentialMode;
import com.otpservice.otp.domain.valueobject.CredentialStatus;
import com.otpservice.otp.domain.valueobject.HmacType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "hmac_credentials")
@CompoundIndex(name = "credential_destination_type_mode_idx",
        def = "{'destination': 1, 'type': 1, 'mode': 1}", unique = true)
public class HmacCredentialDocument {

    @Id
    private String id;
    private String destination;
    private HmacType type;
    private CredentialMode mode;
    private byte[] secretCiphertext;
    private byte[] secretNonce;
    private int digits;
    private int periodSeconds;
    private long counter;
    private long issuedCounter;
    private long lastUsedTimeStep;
    private CredentialStatus status;
    private int failedAttempts;
    private Instant lockedUntil;
    private Instant createdAt;
    private Instant confirmedAt;
}
