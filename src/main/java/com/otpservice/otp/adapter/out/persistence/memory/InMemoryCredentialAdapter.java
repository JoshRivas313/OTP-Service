package com.otpservice.otp.adapter.out.persistence.memory;

import com.otpservice.otp.domain.valueobject.CredentialStatus;
import com.otpservice.otp.adapter.config.OtpProperties;
import com.otpservice.otp.application.port.out.CredentialPersistencePort;
import com.otpservice.otp.domain.model.HmacCredential;
import com.otpservice.otp.domain.valueobject.CredentialMode;
import com.otpservice.otp.domain.valueobject.HmacType;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

// Un solo lock protege todo el estado, igual que InMemoryOtpPersistenceAdapter.
@Component
@Profile("!mongo")
public class InMemoryCredentialAdapter implements CredentialPersistencePort {

  private final int maxEntries;
  private final Map<String, HmacCredential> byKey = new LinkedHashMap<>();

  public InMemoryCredentialAdapter(OtpProperties properties) {
    this.maxEntries = properties.memoryMaxEntries();
  }

  @Override
  public synchronized HmacCredential replace(HmacCredential credential) {
    HmacCredential previous = byKey.remove(key(credential));
    return store(credential.toBuilder().id(previous != null ? previous.getId() : UUID.randomUUID().toString()).build());
  }

  @Override
  public synchronized HmacCredential createIfAbsent(HmacCredential credential) {
    HmacCredential existing = byKey.get(key(credential));
    return existing != null ? existing : store(credential.toBuilder().id(UUID.randomUUID().toString()).build());
  }

  @Override
  public synchronized Optional<HmacCredential> find(String destination, HmacType type, CredentialMode mode) {
    return Optional.ofNullable(byKey.get(key(destination, type, mode)));
  }

  @Override
  public synchronized HmacCredential issue(String id, int digits, int periodSeconds) {
    HmacCredential[] issued = {null};
    update(id, current -> issued[0] = current.toBuilder()
      .issuedCounter(current.getIssuedCounter() + 1)
      .digits(digits)
      .periodSeconds(periodSeconds)
      .failedAttempts(0)
      .lockedUntil(null)
      .build());
    return issued[0];
  }

  @Override
  public synchronized boolean claimTimeStep(String id, long timeStep, Instant activatedAt) {
    return update(id, current -> current.getLastUsedTimeStep() >= timeStep ? null
      : succeeded(current.toBuilder().lastUsedTimeStep(timeStep), activatedAt));
  }

  @Override
  public synchronized boolean claimCounter(String id, long expectedCounter, long nextCounter, Instant activatedAt) {
    return update(id, current -> current.getCounter() != expectedCounter ? null
      : succeeded(current.toBuilder().counter(nextCounter), activatedAt));
  }

  @Override
  public synchronized int registerFailure(String id) {
    int[] attempts = {0};
    update(id, current -> {
      attempts[0] = current.getFailedAttempts() + 1;
      return current.toBuilder().failedAttempts(attempts[0]).build();
    });
    return attempts[0];
  }

  @Override
  public synchronized void lock(String id, Instant until) {
    update(id, current -> current.toBuilder().lockedUntil(until).failedAttempts(0).build());
  }

  @Override
  public synchronized boolean delete(String destination, HmacType type, CredentialMode mode) {
    return byKey.remove(key(destination, type, mode)) != null;
  }

  private HmacCredential store(HmacCredential credential) {
    while (byKey.size() >= maxEntries) {
      Iterator<HmacCredential> oldest = byKey.values().iterator();
      oldest.next();
      oldest.remove();
    }
    byKey.put(key(credential), credential);
    return credential;
  }

  private static HmacCredential succeeded(HmacCredential.HmacCredentialBuilder builder, Instant activatedAt) {
    builder.failedAttempts(0).lockedUntil(null);
    if (activatedAt != null) {
      builder.status(CredentialStatus.ACTIVE).confirmedAt(activatedAt);
    }
    return builder.build();
  }

  private boolean update(String id, UnaryOperator<HmacCredential> change) {
    for (Map.Entry<String, HmacCredential> entry : byKey.entrySet()) {
      if (entry.getValue().getId().equals(id)) {
        HmacCredential updated = change.apply(entry.getValue());
        if (updated == null) {
          return false;
        }
        entry.setValue(updated);
        return true;
      }
    }
    return false;
  }

  private static String key(HmacCredential credential) {
    return key(credential.getDestination(), credential.getType(), credential.getMode());
  }

  private static String key(String destination, HmacType type, CredentialMode mode) {
    return destination + "|" + type + "|" + mode;
  }
}
