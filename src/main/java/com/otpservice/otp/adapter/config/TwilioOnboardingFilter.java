package com.otpservice.otp.adapter.config;

import com.otpservice.otp.adapter.out.sms.twilio.TwilioSessionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class TwilioOnboardingFilter extends OncePerRequestFilter {

    private final TwilioSessionService twilioSessionService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean isOtpServiceRequest = "/otp-service.html".equals(request.getServletPath());
        boolean isEmailChannel = "correo".equals(request.getParameter("canal"));
        if (isOtpServiceRequest && !isEmailChannel && !twilioSessionService.isConnected(request.getSession(true))) {
            response.sendRedirect("/");
            return;
        }
        chain.doFilter(request, response);
    }
}
