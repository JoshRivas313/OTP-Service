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
