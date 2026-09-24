package com.otpservice.otp.config;

import com.otpservice.otp.sms.TwilioSessionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

// index.html (raiz) ya es la pantalla de conectar Twilio, no necesita guardia.
// Este filtro solo protege otp-service.html: sin sesion Twilio conectada,
// no hay forma de saltarse la configuracion escribiendo la URL directo.
@Component
@RequiredArgsConstructor
public class TwilioOnboardingFilter extends OncePerRequestFilter {

    private final TwilioSessionService twilioSessionService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean isOtpServiceRequest = "/otp-service.html".equals(request.getServletPath());
        if (isOtpServiceRequest && !twilioSessionService.isConnected(request.getSession(true))) {
            response.sendRedirect("/");
            return;
        }
        chain.doFilter(request, response);
    }
}
