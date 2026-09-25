package com.otpservice.otp.domain.valueobject;

import java.util.Objects;
import java.util.regex.Pattern;

public final class TwilioCredentials {

    private static final Pattern ACCOUNT_SID = Pattern.compile("^AC[a-zA-Z0-9]{32}$");
    private static final Pattern AUTH_TOKEN = Pattern.compile("^[a-zA-Z0-9]{32}$");
    private static final Pattern VERIFY_SERVICE_SID = Pattern.compile("^VA[a-zA-Z0-9]{32}$");
    private static final Pattern PHONE_NUMBER = Pattern.compile("^\\+[1-9]\\d{6,14}$");

    private final String accountSid;
    private final String authToken;
    private final String verifyServiceSid;
    private final String phoneNumber;

    public TwilioCredentials(String accountSid, String authToken, String verifyServiceSid, String phoneNumber) {
        this.accountSid = requireMatch(accountSid, ACCOUNT_SID, "Account SID");
        this.authToken = requireMatch(authToken, AUTH_TOKEN, "Auth Token");
        this.verifyServiceSid = requireMatch(verifyServiceSid, VERIFY_SERVICE_SID, "Verify Service SID");
        this.phoneNumber = requireMatch(phoneNumber, PHONE_NUMBER, "Número de Twilio");
    }

    public String getAccountSid() {
        return accountSid;
    }

    public String getAuthToken() {
        return authToken;
    }

    public String getVerifyServiceSid() {
        return verifyServiceSid;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String masked() {
        return "%s···%s / verify %s···%s".formatted(
                accountSid.substring(0, 6), accountSid.substring(accountSid.length() - 4),
                verifyServiceSid.substring(0, 6), verifyServiceSid.substring(verifyServiceSid.length() - 4));
    }

    private static String requireMatch(String value, Pattern pattern, String fieldName) {
        String candidate = value == null ? null : value.trim();
        if (candidate == null || !pattern.matcher(candidate).matches()) {
            throw new IllegalArgumentException(fieldName + " de Twilio no tiene un formato válido");
        }
        return candidate;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        return other instanceof TwilioCredentials c
                && accountSid.equals(c.accountSid)
                && authToken.equals(c.authToken)
                && verifyServiceSid.equals(c.verifyServiceSid)
                && phoneNumber.equals(c.phoneNumber);
    }

    @Override
    public int hashCode() {
        return Objects.hash(accountSid, authToken, verifyServiceSid, phoneNumber);
    }

    @Override
    public String toString() {
        return masked();
    }
}
