package com.otpservice.otp.repository;

import com.otpservice.otp.document.OtpDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OtpRepository extends MongoRepository<OtpDocument, String>, OtpRepositoryCustom {

    Optional<OtpDocument> findFirstByCellphoneOrderByValidityWindowGeneratedAtDesc(String cellphone);
}
