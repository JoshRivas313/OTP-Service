package com.otpservice.otp.repository.impl;

import com.otpservice.otp.document.OtpDocument;
import com.otpservice.otp.repository.OtpRepositoryCustom;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.util.Optional;

@RequiredArgsConstructor
public class OtpRepositoryImpl implements OtpRepositoryCustom {

    private static final String FIELD_CELLPHONE = "cellphone";
    private static final String FIELD_CODE_HASH = "codeHash";
    private static final String FIELD_USED = "verificationStatus.used";
    private static final String FIELD_INVALIDATED = "verificationStatus.invalidated";
    private static final String FIELD_ATTEMPTS = "verificationStatus.attempts";
    private static final String FIELD_EXPIRES_AT = "validityWindow.expiresAt";
    private static final String FIELD_GENERATED_AT = "validityWindow.generatedAt";

    private final MongoTemplate mongoTemplate;

    @Override
    public long invalidateActive(String cellphone) {
        Query query = Query.query(Criteria.where(FIELD_CELLPHONE).is(cellphone)
                .and(FIELD_USED).is(false)
                .and(FIELD_INVALIDATED).is(false));

        return mongoTemplate.updateMulti(query, Update.update(FIELD_INVALIDATED, true), OtpDocument.class)
                .getModifiedCount();
    }

    @Override
    public Optional<OtpDocument> claimIfMatches(String id, String codeHash, Instant now, int maxAttempts) {
        Query query = Query.query(Criteria.where("_id").is(id)
                .and(FIELD_CODE_HASH).is(codeHash)
                .and(FIELD_USED).is(false)
                .and(FIELD_INVALIDATED).is(false)
                .and(FIELD_ATTEMPTS).lt(maxAttempts)
                .and(FIELD_EXPIRES_AT).gt(now));

        return Optional.ofNullable(mongoTemplate.findAndModify(
                query,
                Update.update(FIELD_USED, true),
                FindAndModifyOptions.options().returnNew(true),
                OtpDocument.class));
    }

    @Override
    public Optional<OtpDocument> findPreviousWithCode(String cellphone, String codeHash, String excludedId) {
        Query query = Query.query(Criteria.where(FIELD_CELLPHONE).is(cellphone)
                        .and(FIELD_CODE_HASH).is(codeHash)
                        .and("_id").ne(excludedId))
                .with(Sort.by(Sort.Direction.DESC, FIELD_GENERATED_AT))
                .limit(1);

        return Optional.ofNullable(mongoTemplate.findOne(query, OtpDocument.class));
    }

    @Override
    public Optional<OtpDocument> registerFailedAttempt(String id) {
        Query query = Query.query(Criteria.where("_id").is(id)
                .and(FIELD_USED).is(false)
                .and(FIELD_INVALIDATED).is(false));

        return Optional.ofNullable(mongoTemplate.findAndModify(
                query,
                new Update().inc(FIELD_ATTEMPTS, 1),
                FindAndModifyOptions.options().returnNew(true),
                OtpDocument.class));
    }
}
