package com.otpservice.otp.adapter.out.persistence;

import com.otpservice.otp.domain.valueobject.CredentialStatus;
import com.otpservice.otp.adapter.out.persistence.document.HmacCredentialDocument;
import com.otpservice.otp.application.port.out.CredentialPersistencePort;
import com.otpservice.otp.domain.model.HmacCredential;
import com.otpservice.otp.domain.valueobject.CredentialMode;
import com.otpservice.otp.domain.valueobject.EncryptedSecret;
import com.otpservice.otp.domain.valueobject.HmacType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.FindAndReplaceOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
@Profile("mongo")
@RequiredArgsConstructor
public class CredentialPersistenceAdapter implements CredentialPersistencePort {

  private static final String FIELD_ID = "_id";
  private static final String FIELD_DESTINATION = "destination";
  private static final String FIELD_TYPE = "type";
  private static final String FIELD_MODE = "mode";
  private static final String FIELD_DIGITS = "digits";
  private static final String FIELD_PERIOD_SECONDS = "periodSeconds";
  private static final String FIELD_COUNTER = "counter";
  private static final String FIELD_ISSUED_COUNTER = "issuedCounter";
  private static final String FIELD_LAST_USED_TIME_STEP = "lastUsedTimeStep";
  private static final String FIELD_FAILED_ATTEMPTS = "failedAttempts";
  private static final String FIELD_LOCKED_UNTIL = "lockedUntil";
  private static final String FIELD_STATUS = "status";
  private static final String FIELD_CONFIRMED_AT = "confirmedAt";

  private final MongoTemplate mongoTemplate;

  @Override
  public HmacCredential replace(HmacCredential credential) {
    HmacCredentialDocument stored = mongoTemplate.findAndReplace(
      byKey(credential.getDestination(), credential.getType(), credential.getMode()),
      toDocument(credential.toBuilder().id(null).build()),
      FindAndReplaceOptions.options().upsert().returnNew());
    return toDomain(stored);
  }

  @Override
  public HmacCredential createIfAbsent(HmacCredential credential) {
    HmacCredentialDocument doc = toDocument(credential.toBuilder().id(null).build());
    Update update = new Update()
      .setOnInsert("secretCiphertext", doc.getSecretCiphertext())
      .setOnInsert("secretNonce", doc.getSecretNonce())
      .setOnInsert(FIELD_DIGITS, doc.getDigits())
      .setOnInsert(FIELD_PERIOD_SECONDS, doc.getPeriodSeconds())
      .setOnInsert(FIELD_COUNTER, doc.getCounter())
      .setOnInsert(FIELD_ISSUED_COUNTER, doc.getIssuedCounter())
      .setOnInsert(FIELD_LAST_USED_TIME_STEP, doc.getLastUsedTimeStep())
      .setOnInsert(FIELD_STATUS, doc.getStatus())
      .setOnInsert(FIELD_FAILED_ATTEMPTS, doc.getFailedAttempts())
      .setOnInsert("createdAt", doc.getCreatedAt())
      .setOnInsert(FIELD_CONFIRMED_AT, doc.getConfirmedAt());
    return toDomain(mongoTemplate.findAndModify(
      byKey(credential.getDestination(), credential.getType(), credential.getMode()),
      update,
      FindAndModifyOptions.options().upsert(true).returnNew(true),
      HmacCredentialDocument.class));
  }

  @Override
  public Optional<HmacCredential> find(String destination, HmacType type, CredentialMode mode) {
    return Optional.ofNullable(mongoTemplate.findOne(byKey(destination, type, mode), HmacCredentialDocument.class))
      .map(CredentialPersistenceAdapter::toDomain);
  }

  @Override
  public HmacCredential issue(String id, int digits, int periodSeconds) {
    return toDomain(mongoTemplate.findAndModify(
      Query.query(Criteria.where(FIELD_ID).is(id)),
      new Update().inc(FIELD_ISSUED_COUNTER, 1L)
        .set(FIELD_DIGITS, digits)
        .set(FIELD_PERIOD_SECONDS, periodSeconds)
        .set(FIELD_FAILED_ATTEMPTS, 0)
        .unset(FIELD_LOCKED_UNTIL),
      FindAndModifyOptions.options().returnNew(true),
      HmacCredentialDocument.class));
  }

  @Override
  public boolean claimTimeStep(String id, long timeStep, Instant activatedAt) {
    Query query = Query.query(Criteria.where(FIELD_ID).is(id).and(FIELD_LAST_USED_TIME_STEP).lt(timeStep));
    return claim(query, new Update().set(FIELD_LAST_USED_TIME_STEP, timeStep), activatedAt);
  }

  @Override
  public boolean claimCounter(String id, long expectedCounter, long nextCounter, Instant activatedAt) {
    Query query = Query.query(Criteria.where(FIELD_ID).is(id).and(FIELD_COUNTER).is(expectedCounter));
    return claim(query, new Update().set(FIELD_COUNTER, nextCounter), activatedAt);
  }

  @Override
  public int registerFailure(String id) {
    HmacCredentialDocument updated = mongoTemplate.findAndModify(
      Query.query(Criteria.where(FIELD_ID).is(id)),
      new Update().inc(FIELD_FAILED_ATTEMPTS, 1),
      FindAndModifyOptions.options().returnNew(true),
      HmacCredentialDocument.class);
    return updated == null ? 0 : updated.getFailedAttempts();
  }

  @Override
  public void lock(String id, Instant until) {
    mongoTemplate.updateFirst(
      Query.query(Criteria.where(FIELD_ID).is(id)),
      new Update().set(FIELD_LOCKED_UNTIL, until).set(FIELD_FAILED_ATTEMPTS, 0),
      HmacCredentialDocument.class);
  }

  @Override
  public boolean delete(String destination, HmacType type, CredentialMode mode) {
    return mongoTemplate.remove(byKey(destination, type, mode), HmacCredentialDocument.class).getDeletedCount() > 0;
  }

  private boolean claim(Query query, Update update, Instant activatedAt) {
    update.set(FIELD_FAILED_ATTEMPTS, 0).unset(FIELD_LOCKED_UNTIL);
    if (activatedAt != null) {
      update.set(FIELD_STATUS, CredentialStatus.ACTIVE).set(FIELD_CONFIRMED_AT, activatedAt);
    }
    return mongoTemplate.updateFirst(query, update, HmacCredentialDocument.class).getModifiedCount() == 1;
  }

  private static Query byKey(String destination, HmacType type, CredentialMode mode) {
    return Query.query(Criteria.where(FIELD_DESTINATION).is(destination)
      .and(FIELD_TYPE).is(type)
      .and(FIELD_MODE).is(mode));
  }

  private static HmacCredentialDocument toDocument(HmacCredential c) {
    return new HmacCredentialDocument(
      c.getId(), c.getDestination(), c.getType(), c.getMode(),
      c.getSecret().ciphertext(), c.getSecret().nonce(),
      c.getDigits(), c.getPeriodSeconds(), c.getCounter(), c.getIssuedCounter(), c.getLastUsedTimeStep(),
      c.getStatus(), c.getFailedAttempts(), c.getLockedUntil(), c.getCreatedAt(), c.getConfirmedAt());
  }

  private static HmacCredential toDomain(HmacCredentialDocument d) {
    return HmacCredential.builder()
      .id(d.getId())
      .destination(d.getDestination())
      .type(d.getType())
      .mode(d.getMode())
      .secret(new EncryptedSecret(d.getSecretCiphertext(), d.getSecretNonce()))
      .digits(d.getDigits())
      .periodSeconds(d.getPeriodSeconds())
      .counter(d.getCounter())
      .issuedCounter(d.getIssuedCounter())
      .lastUsedTimeStep(d.getLastUsedTimeStep())
      .status(d.getStatus())
      .failedAttempts(d.getFailedAttempts())
      .lockedUntil(d.getLockedUntil())
      .createdAt(d.getCreatedAt())
      .confirmedAt(d.getConfirmedAt())
      .build();
  }
}
