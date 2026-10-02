package com.otpservice.otp.application.port.out;

import com.otpservice.otp.domain.model.HmacCredential;
import com.otpservice.otp.domain.valueobject.HmacType;
import com.otpservice.otp.domain.valueobject.Purpose;

import java.time.Instant;
import java.util.Optional;

// claimTimeStep y claimCounter son atomicos: evitan que dos peticiones acepten el mismo codigo.
public interface CredentialPersistencePort {

  HmacCredential createIfAbsent(HmacCredential credential);

  Optional<HmacCredential> find(String destination, HmacType type, Purpose purpose);

  // No toca los intentos ni el bloqueo. advanceCounter: solo HOTP avanza el contador al emitir.
  HmacCredential issue(String id, int digits, int periodSeconds, boolean advanceCounter);

  boolean claimTimeStep(String id, long timeStep);

  boolean claimCounter(String id, long expectedCounter, long nextCounter);

  int registerFailure(String id);

  void lock(String id, Instant until);
}
