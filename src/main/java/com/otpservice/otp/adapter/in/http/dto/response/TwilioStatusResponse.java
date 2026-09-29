package com.otpservice.otp.adapter.in.http.dto.response;

import java.util.List;

public record TwilioStatusResponse(boolean connected, String maskedCredentials,
                                   boolean restrictedToVerifiedNumbers, List<String> verifiedNumbers) {

    public static TwilioStatusResponse connected(String maskedCredentials, boolean restricted, List<String> verifiedNumbers) {
        return new TwilioStatusResponse(true, maskedCredentials, restricted, verifiedNumbers);
    }

    public static TwilioStatusResponse disconnected() {
        return new TwilioStatusResponse(false, null, false, List.of());
    }
}
