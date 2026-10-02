package com.otpservice.otp.domain.model;

import com.otpservice.otp.domain.exception.OtpAlreadyUsedException;
import com.otpservice.otp.domain.exception.OtpBlockedException;
import com.otpservice.otp.domain.exception.OtpDomainException;
import com.otpservice.otp.domain.exception.OtpExpiredException;
import com.otpservice.otp.domain.exception.OtpInvalidatedException;

import java.time.Instant;
import java.util.Arrays;

// Estado de un codigo en un momento dado. El orden de declaracion es la prioridad: si varios aplican a la vez,
// se informa el primero, y eso decide que error ve el cliente.
public enum OtpStatus {

    INVALIDATED {
        @Override
        boolean appliesTo(Otp otp, Instant now, int maxAllowedAttempts) {
            return otp.isInvalidated();
        }

        @Override
        public OtpDomainException rejection() {
            return new OtpInvalidatedException();
        }
    },

    USED {
        @Override
        boolean appliesTo(Otp otp, Instant now, int maxAllowedAttempts) {
            return otp.isUsed();
        }

        @Override
        public OtpDomainException rejection() {
            return new OtpAlreadyUsedException();
        }
    },

    EXPIRED {
        @Override
        boolean appliesTo(Otp otp, Instant now, int maxAllowedAttempts) {
            return otp.isExpired(now);
        }

        @Override
        public OtpDomainException rejection() {
            return new OtpExpiredException();
        }
    },

    BLOCKED {
        @Override
        boolean appliesTo(Otp otp, Instant now, int maxAllowedAttempts) {
            return otp.isBlocked(maxAllowedAttempts);
        }

        @Override
        public OtpDomainException rejection() {
            return new OtpBlockedException();
        }
    },

    ACTIVE {
        @Override
        boolean appliesTo(Otp otp, Instant now, int maxAllowedAttempts) {
            return true;
        }
    };

    abstract boolean appliesTo(Otp otp, Instant now, int maxAllowedAttempts);

    public static OtpStatus of(Otp otp, Instant now, int maxAllowedAttempts) {
        return Arrays.stream(values())
                .filter(status -> status.appliesTo(otp, now, maxAllowedAttempts))
                .findFirst()
                .orElseThrow();
    }

    public boolean isVerifiable() {
        return this == ACTIVE;
    }

    // El error que corresponde a un codigo que no se puede verificar.
    public OtpDomainException rejection() {
        throw new IllegalStateException("Un código activo no se rechaza");
    }
}
