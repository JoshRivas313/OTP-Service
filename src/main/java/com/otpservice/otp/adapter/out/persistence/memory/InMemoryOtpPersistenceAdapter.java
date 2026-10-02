package com.otpservice.otp.adapter.out.persistence.memory;

import com.otpservice.otp.adapter.config.OtpProperties;
import com.otpservice.otp.application.port.out.OtpPersistencePort;
import com.otpservice.otp.domain.model.Otp;
import com.otpservice.otp.domain.valueobject.Purpose;
import com.otpservice.otp.domain.valueobject.ValidityWindow;
import com.otpservice.otp.domain.valueobject.VerificationStatus;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// Un solo lock protege todo el estado: cada operacion del puerto es atomica, igual que en MongoDB.
@Component
@Profile("!mongo")
public class InMemoryOtpPersistenceAdapter implements OtpPersistencePort {

  private final Clock clock;
  private final int maxEntries;
  private final Map<String, Entry> entries = new LinkedHashMap<>();

  public InMemoryOtpPersistenceAdapter(Clock clock, OtpProperties properties) {
    this.clock = clock;
    this.maxEntries = properties.memoryMaxEntries();
  }

  @Override
  public synchronized long invalidateActive(String destination, Purpose purpose) {
    long invalidated = 0;
    for (Entry entry : entries.values()) {
      if (entry.matches(destination, purpose) && !entry.used && !entry.invalidated) {
        entry.invalidated = true;
        invalidated++;
      }
    }
    return invalidated;
  }

  @Override
  public synchronized void save(Otp otp) {
    purgeExpired();
    while (entries.size() >= maxEntries) {
      Iterator<Entry> oldest = entries.values().iterator();
      oldest.next();
      oldest.remove();
    }
    String id = otp.getId() != null ? otp.getId() : UUID.randomUUID().toString();
    entries.put(id, Entry.of(id, otp));
  }

  @Override
  public synchronized Optional<Otp> findLatest(String destination, Purpose purpose) {
    Instant now = clock.instant();
    Entry latest = null;
    for (Entry entry : entries.values()) {
      if (!entry.matches(destination, purpose) || !entry.purgeAt.isAfter(now)) {
        continue;
      }
      if (latest == null || !entry.validityWindow.getGeneratedAt().isBefore(latest.validityWindow.getGeneratedAt())) {
        latest = entry;
      }
    }
    return Optional.ofNullable(latest).map(Entry::toOtp);
  }

  @Override
  public synchronized Optional<Otp> claimIfMatches(String id, String codeHash, Instant now, int maxAttempts) {
    Entry entry = entries.get(id);
    if (entry == null
        || entry.used
        || entry.invalidated
        || entry.attempts >= maxAttempts
        || !entry.validityWindow.getExpiresAt().isAfter(now)
        || !sameHash(entry.codeHash, codeHash)) {
      return Optional.empty();
    }
    entry.used = true;
    return Optional.of(entry.toOtp());
  }

  @Override
  public synchronized Optional<Otp> registerFailedAttempt(String id) {
    Entry entry = entries.get(id);
    if (entry == null || entry.used || entry.invalidated) {
      return Optional.empty();
    }
    entry.attempts++;
    return Optional.of(entry.toOtp());
  }

  private void purgeExpired() {
    Instant now = clock.instant();
    entries.values().removeIf(entry -> !entry.purgeAt.isAfter(now));
  }

  private static boolean sameHash(String expected, String actual) {
    return MessageDigest.isEqual(
      expected.getBytes(StandardCharsets.UTF_8),
      actual.getBytes(StandardCharsets.UTF_8));
  }

  private static final class Entry {

    private final String id;
    private final String destination;
    private final Purpose purpose;
    private final String codeHash;
    private final int digits;
    private final ValidityWindow validityWindow;
    private final Instant purgeAt;
    private int attempts;
    private boolean used;
    private boolean invalidated;

    private Entry(String id, Otp otp) {
      this.id = id;
      this.destination = otp.getDestination();
      this.purpose = otp.getPurpose();
      this.codeHash = otp.getCodeHash();
      this.digits = otp.getDigits();
      this.validityWindow = otp.getValidityWindow();
      this.purgeAt = otp.getPurgeAt();
      this.attempts = otp.getAttempts();
      this.used = otp.isUsed();
      this.invalidated = otp.isInvalidated();
    }

    boolean matches(String destination, Purpose purpose) {
      return this.destination.equals(destination) && this.purpose == purpose;
    }

    static Entry of(String id, Otp otp) {
      return new Entry(id, otp);
    }

    Otp toOtp() {
      return Otp.builder()
        .id(id)
        .destination(destination)
        .purpose(purpose)
        .codeHash(codeHash)
        .digits(digits)
        .validityWindow(validityWindow)
        .verificationStatus(new VerificationStatus(attempts, used, invalidated))
        .purgeAt(purgeAt)
        .build();
    }
  }
}
