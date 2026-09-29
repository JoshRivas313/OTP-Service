package com.otpservice.otp.adapter.out.sms.twilio;

import com.otpservice.otp.domain.valueobject.Cellphone;

import java.util.Set;

public record TwilioAccountInfo(boolean restricted, Set<String> verifiedNumbers) {

    public static TwilioAccountInfo unrestricted() {
        return new TwilioAccountInfo(false, Set.of());
    }

    public boolean allows(Cellphone destination) {
        return !restricted || verifiedNumbers.contains(destination.getValue());
    }
}
