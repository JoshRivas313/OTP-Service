package com.otpservice.otp.adapter.in.http;

import com.otpservice.otp.adapter.out.sms.twilio.TwilioAccountInfo;
import com.otpservice.otp.adapter.out.sms.twilio.TwilioVerifyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// La sesion HTTP existe solo para quien conecto Twilio: leer el estado o desconectar no crea una por cada visita anonima.
@ActiveProfiles("dev")
@SpringBootTest(properties = {
        "otp.demo-mode=true",
        "otp.rate-limit-per-destination=0",
        "otp.rate-limit-per-ip=0"
})
@AutoConfigureMockMvc
class TwilioSessionHttpTest {

    private static final String CREDENTIALS = "{\"accountSid\":\"AC" + "a".repeat(32) + "\","
            + "\"authToken\":\"" + "b".repeat(32) + "\","
            + "\"verifyServiceSid\":\"VA" + "c".repeat(32) + "\","
            + "\"phoneNumber\":\"+15017122661\"}";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private TwilioVerifyService verifyService;

    private MvcResult connect(MockHttpSession session) throws Exception {
        given(verifyService.fetchAccountInfo(any())).willReturn(new TwilioAccountInfo(false, Set.of()));
        var request = MockMvcRequestBuilders.post("/api/twilio/connect")
                .contentType(MediaType.APPLICATION_JSON).content(CREDENTIALS);
        if (session != null) {
            request.session(session);
        }
        return mvc.perform(request).andExpect(status().isOk()).andExpect(jsonPath("$.connected").value(true))
                .andReturn();
    }

    @Test
    void consultarElEstadoSinCookieNoCreaNingunaSesion() throws Exception {
        MvcResult result = mvc.perform(MockMvcRequestBuilders.get("/api/twilio/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connected").value(false))
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andReturn();

        assertThat(result.getRequest().getSession(false)).isNull();
    }

    @Test
    void desconectarSinSesionNoCreaNingunaSesion() throws Exception {
        MvcResult result = mvc.perform(MockMvcRequestBuilders.post("/api/twilio/disconnect"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connected").value(false))
                .andReturn();

        assertThat(result.getRequest().getSession(false)).isNull();
    }

    @Test
    void enviarSinHaberConectadoResponde400SinCrearSesion() throws Exception {
        MvcResult result = mvc.perform(MockMvcRequestBuilders.post("/api/twilio/otps")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"cellphone\":\"987654321\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TWILIO_NOT_CONNECTED"))
                .andReturn();

        assertThat(result.getRequest().getSession(false)).isNull();
    }

    @Test
    void conectarCreaLaSesionConUnTiempoCortoYElEstadoLaVe() throws Exception {
        MockHttpSession session = (MockHttpSession) connect(null).getRequest().getSession(false);

        assertThat(session).isNotNull();
        assertThat(session.getMaxInactiveInterval()).isEqualTo(15 * 60);
        mvc.perform(MockMvcRequestBuilders.get("/api/twilio/status").session(session))
                .andExpect(jsonPath("$.connected").value(true));
    }

    @Test
    void alConectarElIdentificadorDeSesionCambia() throws Exception {
        MockHttpSession previous = new MockHttpSession();
        String previousId = previous.getId();

        MockHttpSession connected = (MockHttpSession) connect(previous).getRequest().getSession(false);

        assertThat(connected.getId()).isNotEqualTo(previousId);
    }

    @Test
    void desconectarCierraLaSesion() throws Exception {
        MockHttpSession session = (MockHttpSession) connect(null).getRequest().getSession(false);

        mvc.perform(MockMvcRequestBuilders.post("/api/twilio/disconnect").session(session))
                .andExpect(jsonPath("$.connected").value(false));

        assertThat(session.isInvalid()).isTrue();
        mvc.perform(MockMvcRequestBuilders.get("/api/twilio/status").session(session))
                .andExpect(jsonPath("$.connected").value(false));
    }
}
