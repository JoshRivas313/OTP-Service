package com.otpservice.otp.sms.twilioconnect;

import com.otpservice.otp.domain.valueobject.TwilioCredentials;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class TwilioSessionService {

    private static final String SESSION_KEY = "twilio.connect.credentials";

    public void connect(HttpSession session, TwilioCredentials credentials) {
        session.setAttribute(SESSION_KEY, credentials);
    }

    public void disconnect(HttpSession session) {
        session.removeAttribute(SESSION_KEY);
    }

    public Optional<TwilioCredentials> get(HttpSession session) {
        return Optional.ofNullable((TwilioCredentials) session.getAttribute(SESSION_KEY));
    }

    public boolean isConnected(HttpSession session) {
        return get(session).isPresent();
    }
}
