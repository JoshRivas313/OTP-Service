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

// Con el flag prendido, tanto "/" como "/index.html" mandan a conectar
// Twilio primero si esa sesion todavia no conecto nada. Una vez conectada
// (o con el flag apagado) pasa de largo y sirve el index normal.
@Component
@RequiredArgsConstructor
public class TwilioOnboardingFilter extends OncePerRequestFilter {

    private final TwilioConnectProperties twilioConnectProperties;
    private final TwilioSessionService twilioSessionService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean isIndexRequest = "/".equals(request.getServletPath()) || "/index.html".equals(request.getServletPath());
        if (twilioConnectProperties.enabled() && isIndexRequest
                && !twilioSessionService.isConnected(request.getSession(true))) {
            response.sendRedirect("/twilio.html");
            return;
        }
        chain.doFilter(request, response);
    }
}
