package com.otpservice.otp.adapter.in.http;

import com.otpservice.otp.adapter.exception.EmailDeliveryFailedException;
import com.otpservice.otp.adapter.exception.SmsDeliveryFailedException;
import com.otpservice.otp.adapter.out.sms.twilio.TwilioAccountInfo;
import com.otpservice.otp.adapter.out.sms.twilio.TwilioSessionSmsSender;
import com.otpservice.otp.adapter.out.sms.twilio.TwilioVerifyService;
import com.otpservice.otp.application.port.out.EmailSender;
import com.otpservice.otp.application.port.out.SmsSender;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Un envio que el proveedor no pudo entregar no gasta el limite por destino (aqui, 1): no fue un abuso. La devolucion del
// tope diario se prueba en SendLimitsTest, porque ese tope es global y se compartiria entre los tests de esta clase.
@ActiveProfiles("dev")
@SpringBootTest(properties = {
        "otp.demo-mode=true",
        "otp.rate-limit-per-destination=1",
        "otp.rate-limit-per-ip=0"
})
@AutoConfigureMockMvc
class DeliveryFailureHttpTest {

    private static final String CREDENTIALS = "{\"accountSid\":\"AC" + "a".repeat(32) + "\","
            + "\"authToken\":\"" + "b".repeat(32) + "\","
            + "\"verifyServiceSid\":\"VA" + "c".repeat(32) + "\","
            + "\"phoneNumber\":\"+15017122661\"}";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private EmailSender emailSender;

    @MockitoBean
    private SmsSender smsSender;

    @MockitoBean
    private TwilioVerifyService verifyService;

    @MockitoBean
    private TwilioSessionSmsSender sessionSmsSender;

    private ResultActions post(String url, String body) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post(url).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void siElCorreoNoSeEntregaElReintentoNoSeRechazaPorLimites() throws Exception {
        willThrow(new EmailDeliveryFailedException()).given(emailSender).send(any(), any());
        post("/api/email/otps", "{\"email\":\"falla@gmail.com\"}")
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("EMAIL_DELIVERY_FAILED"));

        // Con limite de 1 por destino, sin devolucion este segundo intento seria 429.
        org.mockito.Mockito.reset(emailSender);
        post("/api/email/otps", "{\"email\":\"falla@gmail.com\"}").andExpect(status().isCreated());
    }

    @Test
    void siElSmsNoSeEntregaElReintentoNoSeRechazaPorLimites() throws Exception {
        willThrow(new SmsDeliveryFailedException()).given(smsSender).send(any(), any());
        post("/otps", "{\"cellphone\":\"987654321\"}")
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("SMS_DELIVERY_FAILED"));

        org.mockito.Mockito.reset(smsSender);
        post("/otps", "{\"cellphone\":\"987654322\"}").andExpect(status().isCreated());
    }

    @Test
    void conTwilioPorSesionElEnvioFallidoTambienDevuelveLaPlaza() throws Exception {
        given(verifyService.fetchAccountInfo(any())).willReturn(new TwilioAccountInfo(false, Set.of()));
        MockHttpSession session = (MockHttpSession) post("/api/twilio/connect", CREDENTIALS)
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
        willThrow(new SmsDeliveryFailedException("Twilio rechazó el envío")).given(sessionSmsSender).send(any(), any(), any());

        mvc.perform(MockMvcRequestBuilders.post("/api/twilio/otps").session(session)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"cellphone\":\"987654323\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("Twilio rechazó el envío"));

        org.mockito.Mockito.reset(sessionSmsSender);
        mvc.perform(MockMvcRequestBuilders.post("/api/twilio/otps").session(session)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"cellphone\":\"987654323\"}"))
                .andExpect(status().isCreated());
        verify(sessionSmsSender).send(any(), any(), any());
    }

    @Test
    void unaCuentaDePruebaSoloEnviaASusNumerosVerificados() throws Exception {
        given(verifyService.fetchAccountInfo(any())).willReturn(new TwilioAccountInfo(true, Set.of("+51912345678")));
        MockHttpSession session = (MockHttpSession) post("/api/twilio/connect", CREDENTIALS)
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);

        mvc.perform(MockMvcRequestBuilders.post("/api/twilio/otps").session(session)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"cellphone\":\"987000111\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("DESTINATION_NOT_VERIFIED"));
    }
}
