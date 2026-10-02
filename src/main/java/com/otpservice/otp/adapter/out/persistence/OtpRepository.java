package com.otpservice.otp.adapter.out.persistence;

import com.otpservice.otp.adapter.out.persistence.document.OtpDocument;
import com.otpservice.otp.domain.valueobject.Purpose;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OtpRepository extends MongoRepository<OtpDocument, String>, OtpRepositoryCustom {

    Optional<OtpDocument> findFirstByDestinationAndPurposeOrderByValidityWindowGeneratedAtDesc(String destination, Purpose purpose);
}
