package com.otpservice.otp.adapter.out.persistence.memory;

import com.otpservice.otp.adapter.config.OtpProperties;
import com.otpservice.otp.application.port.out.CredentialPersistencePort;
import com.otpservice.otp.domain.model.HmacCredential;
import com.otpservice.otp.domain.valueobject.HmacType;
import com.otpservice.otp.domain.valueobject.Purpose;
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
// Al llegar al tope se expulsa la credencial menos usada (orden de acceso), no la mas antigua: una con codigos
// pendientes de un usuario activo no debe perderse por que otros destinos se crearon despues.
@Component
@Profile("!mongo")
public class InMemoryCredentialAdapter implements CredentialPersistencePort {

  private final int maxEntries;
  private final Map<String, HmacCredential> byKey = new LinkedHashMap<>(16, 0.75f, true);

  public InMemoryCredentialAdapter(OtpProperties properties) {
    this.maxEntries = properties.memoryMaxEntries();
  }

  @Override
  public synchronized HmacCredential createIfAbsent(HmacCredential credential) {
    HmacCredential existing = byKey.get(key(credential.getDestination(), credential.getType(), credential.getPurpose()));
    return existing != null ? existing : store(credential.toBuilder().id(UUID.randomUUID().toString()).build());
  }

  @Override
  public synchronized Optional<HmacCredential> find(String destination, HmacType type, Purpose purpose) {
    return Optional.ofNullable(byKey.get(key(destination, type, purpose)));
  }

  @Override
  public synchronized HmacCredential issue(String id, int digits, int periodSeconds, boolean advanceCounter) {
    return update(id, current -> current.toBuilder()
      .issuedCounter(advanceCounter ? current.getIssuedCounter() + 1 : current.getIssuedCounter())
      .digits(digits)
      .periodSeconds(periodSeconds)
      .build())
      .orElseThrow();
  }

  @Override
  public synchronized boolean claimTimeStep(String id, long timeStep) {
    return update(id, current -> current.getLastUsedTimeStep() >= timeStep ? null
      : succeeded(current.toBuilder().lastUsedTimeStep(timeStep))).isPresent();
  }

  @Override
  public synchronized boolean claimCounter(String id, long expectedCounter, long nextCounter) {
    return update(id, current -> current.getCounter() != expectedCounter ? null
      : succeeded(current.toBuilder().counter(nextCounter))).isPresent();
  }

  @Override
  public synchronized int registerFailure(String id) {
    return update(id, current -> current.toBuilder().failedAttempts(current.getFailedAttempts() + 1).build())
      .map(HmacCredential::getFailedAttempts)
      .orElse(0);
  }

  @Override
  public synchronized void lock(String id, Instant until) {
    update(id, current -> current.toBuilder().lockedUntil(until).failedAttempts(0).build());
  }

  private HmacCredential store(HmacCredential credential) {
    while (byKey.size() >= maxEntries) {
      Iterator<HmacCredential> oldest = byKey.values().iterator();
      oldest.next();
      oldest.remove();
    }
    byKey.put(key(credential.getDestination(), credential.getType(), credential.getPurpose()), credential);
    return credential;
  }

  private static HmacCredential succeeded(HmacCredential.HmacCredentialBuilder builder) {
    return builder.failedAttempts(0).lockedUntil(null).build();
  }

  // change devuelve null cuando la condicion no se cumple: no se modifica nada y el resultado queda vacio.
  private Optional<HmacCredential> update(String id, UnaryOperator<HmacCredential> change) {
    for (Map.Entry<String, HmacCredential> entry : byKey.entrySet()) {
      if (entry.getValue().getId().equals(id)) {
        HmacCredential updated = change.apply(entry.getValue());
        if (updated != null) {
          entry.setValue(updated);
          byKey.get(entry.getKey());          // cuenta como uso; se sale del bucle justo despues
        }
        return Optional.ofNullable(updated);
      }
    }
    return Optional.empty();
  }

  private static String key(String destination, HmacType type, Purpose purpose) {
    return destination + "|" + type + "|" + purpose;
  }
}
