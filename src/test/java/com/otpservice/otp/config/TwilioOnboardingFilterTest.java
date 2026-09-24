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

    @BeforeEach
    void setUp() {
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        chain = mock(FilterChain.class);
        session = mock(HttpSession.class);
        sessionService = mock(TwilioSessionService.class);
        when(request.getSession(true)).thenReturn(session);
    }

    @Test
    void conFlagApagadoDejaPasarAunSinConectar() throws Exception {
        when(request.getServletPath()).thenReturn("/");
        TwilioOnboardingFilter filter = filter(false);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).sendRedirect(anyString());
    }

    @Test
    void conFlagPrendidoYSinConectarRedirigeAConectar() throws Exception {
        when(request.getServletPath()).thenReturn("/");
        when(sessionService.isConnected(session)).thenReturn(false);
        TwilioOnboardingFilter filter = filter(true);

        filter.doFilter(request, response, chain);

        verify(response).sendRedirect("/twilio.html");
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void indexHtmlDirectoTambienQuedaGateado() throws Exception {
        when(request.getServletPath()).thenReturn("/index.html");
        when(sessionService.isConnected(session)).thenReturn(false);
        TwilioOnboardingFilter filter = filter(true);

        filter.doFilter(request, response, chain);

        verify(response).sendRedirect("/twilio.html");
    }

    @Test
    void conFlagPrendidoYYaConectadoDejaPasar() throws Exception {
        when(request.getServletPath()).thenReturn("/");
        when(sessionService.isConnected(session)).thenReturn(true);
        TwilioOnboardingFilter filter = filter(true);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).sendRedirect(anyString());
    }

    @Test
    void laPaginaDeTwilioNuncaSeIntercepta() throws Exception {
        when(request.getServletPath()).thenReturn("/twilio.html");
        TwilioOnboardingFilter filter = filter(true);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).sendRedirect(anyString());
    }

    private TwilioOnboardingFilter filter(boolean enabled) {
        return new TwilioOnboardingFilter(new TwilioConnectProperties(enabled), sessionService);
    }
}
