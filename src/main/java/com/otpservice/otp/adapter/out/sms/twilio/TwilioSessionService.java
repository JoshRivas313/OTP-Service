package com.otpservice.otp.adapter.out.sms.twilio;

import com.otpservice.otp.domain.valueobject.TwilioCredentials;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class TwilioSessionService {

    private static final String SESSION_KEY = "twilio.connect.credentials";
    private static final String ACCOUNT_INFO_KEY = "twilio.connect.account-info";

    public void connect(HttpSession session, TwilioCredentials credentials, TwilioAccountInfo accountInfo) {
        session.setAttribute(SESSION_KEY, credentials);
        session.setAttribute(ACCOUNT_INFO_KEY, accountInfo);
    }

    // La sesion puede no existir: leer o desconectar no debe crear una.
    public void disconnect(HttpSession session) {
        if (session != null) {
            session.removeAttribute(SESSION_KEY);
            session.removeAttribute(ACCOUNT_INFO_KEY);
        }
    }

    public TwilioAccountInfo accountInfo(HttpSession session) {
        return Optional.ofNullable(session)
                .map(current -> (TwilioAccountInfo) current.getAttribute(ACCOUNT_INFO_KEY))
                .orElseGet(TwilioAccountInfo::unrestricted);
    }

    public Optional<TwilioCredentials> get(HttpSession session) {
        return Optional.ofNullable(session)
                .map(current -> (TwilioCredentials) current.getAttribute(SESSION_KEY));
    }

}
