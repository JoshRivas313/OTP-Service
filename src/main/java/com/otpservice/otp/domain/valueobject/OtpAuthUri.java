package com.otpservice.otp.domain.valueobject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class OtpAuthUri {

    private OtpAuthUri() {
    }

    public static String totp(String issuer, String account, AuthenticatorSecret secret, int digits, int periodSeconds) {
        return base(HmacType.TOTP, issuer, account, secret, digits) + "&period=" + periodSeconds;
    }

    public static String hotp(String issuer, String account, AuthenticatorSecret secret, int digits, long counter) {
        return base(HmacType.HOTP, issuer, account, secret, digits) + "&counter=" + counter;
    }

    private static String base(HmacType type, String issuer, String account,
                               AuthenticatorSecret secret, int digits) {
        return "otpauth://" + type.uriName() + "/" + encode(issuer) + ":" + encode(account)
                + "?secret=" + secret.base32()
                + "&issuer=" + encode(issuer)
                + "&algorithm=SHA1"
                + "&digits=" + digits;
    }

    // URLEncoder pone "+" en los espacios y las apps lo muestran literal.
    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
