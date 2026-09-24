package com.otpservice.otp.config;

import com.otpservice.otp.sms.TwilioSessionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TwilioOnboardingFilterTest {

    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain chain;
    private HttpSession session;
    private TwilioSessionService sessionService;
    private TwilioOnboardingFilter filter;

    @BeforeEach
    void setUp() {
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        chain = mock(FilterChain.class);
        session = mock(HttpSession.class);
        sessionService = mock(TwilioSessionService.class);
        when(request.getSession(true)).thenReturn(session);
        filter = new TwilioOnboardingFilter(sessionService);
    }

    @Test
    void otpServiceSinConectarRedirigeALaRaiz() throws Exception {
        when(request.getServletPath()).thenReturn("/otp-service.html");
        when(sessionService.isConnected(session)).thenReturn(false);

        filter.doFilter(request, response, chain);

        verify(response).sendRedirect("/");
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void otpServiceYaConectadoDejaPasar() throws Exception {
        when(request.getServletPath()).thenReturn("/otp-service.html");
        when(sessionService.isConnected(session)).thenReturn(true);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).sendRedirect(anyString());
    }

    @Test
    void laRaizNuncaSeIntercepta() throws Exception {
        when(request.getServletPath()).thenReturn("/");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).sendRedirect(anyString());
    }
}
