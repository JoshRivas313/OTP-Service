package com.otpservice.otp.adapter.out.sms;

import com.otpservice.otp.adapter.config.SmsProperties;
import com.otpservice.otp.application.port.out.SmsSender;
import com.otpservice.otp.domain.valueobject.Cellphone;
import com.otpservice.otp.adapter.exception.SmsDeliveryFailedException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.time.Duration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "sms.provider", havingValue = "infobip")
public class InfobipSmsSender implements SmsSender {

    private final SmsProperties properties;
    private RestClient restClient;

    @PostConstruct
    void initialize() {
        SmsProperties.Infobip infobip = properties.infobip();
        if (infobip.baseUrl().isBlank() || infobip.apiKey().isBlank() || infobip.sender().isBlank()) {
            throw new IllegalStateException("sms.provider=infobip requiere INFOBIP_BASE_URL, "
                    + "INFOBIP_API_KEY e INFOBIP_SENDER");
        }
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(15));
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(infobip.baseUrl())
                .defaultHeader("Authorization", "App " + infobip.apiKey())
                .defaultHeader("Content-Type", "application/json")
                .build();
        log.info("Proveedor de SMS = infobip");
    }

    @Override
    public void send(Cellphone destination, String message) {
        try {
            restClient.post()
                    .uri("/sms/3/messages")
                    .body(requestBody(destination, message))
                    .retrieve()
                    .toBodilessEntity();

            log.info("SMS entregado a Infobip para={}", destination.masked());
        } catch (RestClientException exception) {
            log.warn("No se pudo entregar el SMS a Infobip para={}", destination.masked());
            throw new SmsDeliveryFailedException();
        }
    }

    private Map<String, Object> requestBody(Cellphone destination, String message) {
        String bareNumber = destination.getValue().replace("+", "");
        return Map.of("messages", List.of(Map.of(
                "destinations", List.of(Map.of("to", bareNumber)),
                "sender", properties.infobip().sender(),
                "content", Map.of("text", message))));
    }
}
