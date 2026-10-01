package com.otpservice.otp.application.port.out;

import com.otpservice.otp.domain.model.HmacCredential;
import com.otpservice.otp.domain.valueobject.CredentialMode;
import com.otpservice.otp.domain.valueobject.HmacType;

import java.time.Instant;
import java.util.Optional;

// claimTimeStep y claimCounter son atomicos: evitan que dos peticiones acepten el mismo codigo.
public interface CredentialPersistencePort {

  HmacCredential replace(HmacCredential credential);

  HmacCredential createIfAbsent(HmacCredential credential);

  Optional<HmacCredential> find(String destination, HmacType type, CredentialMode mode);

  HmacCredential issue(String id, int digits, int periodSeconds);

  boolean claimTimeStep(String id, long timeStep, Instant activatedAt);

  boolean claimCounter(String id, long expectedCounter, long nextCounter, Instant activatedAt);

  int registerFailure(String id);

  void lock(String id, Instant until);

  boolean delete(String destination, HmacType type, CredentialMode mode);
}
