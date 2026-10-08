package com.otpservice.otp.adapter.out.email;

import com.otpservice.otp.adapter.config.EmailProperties;
import com.otpservice.otp.adapter.exception.EmailDeliveryFailedException;
import com.otpservice.otp.application.port.out.EmailSender;
import com.otpservice.otp.domain.valueobject.EmailAddress;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "email.provider", havingValue = "brevo")
public class BrevoEmailSender implements EmailSender {

    private static final String SUBJECT = "Tu código de verificación";

    private final EmailProperties properties;
    private RestClient restClient;

    @PostConstruct
    void initialize() {
        if (properties.brevo().apiKey().isBlank() || properties.senderAddress().isBlank()) {
            throw new IllegalStateException("email.provider=brevo requiere BREVO_API_KEY y EMAIL_SENDER_ADDRESS");
        }
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(15));
        this.restClient = RestClient.builder()
                .baseUrl(properties.brevo().baseUrl())
                .requestFactory(new BufferingClientHttpRequestFactory(requestFactory))
                .defaultHeader("api-key", properties.brevo().apiKey())
                .defaultHeader("Accept", "application/json")
                .build();
        log.info("Proveedor de correo = brevo");
    }

    @Override
    public void send(EmailAddress destination, String message) {
        try {
            restClient.post()
                    .uri("/v3/smtp/email")
                    .header("Content-Type", "application/json")
                    .body(requestBody(destination, message))
                    .retrieve()
                    .toBodilessEntity();

            log.info("Correo entregado a Brevo para={}", destination.masked());
        } catch (RestClientResponseException exception) {
            log.warn("Brevo rechazo el envio para={} estado={}: {}",
                    destination.masked(), exception.getStatusCode().value(),
                    exception.getResponseBodyAsString().replace(destination.getValue(), destination.masked()));
            throw new EmailDeliveryFailedException();
        } catch (RestClientException exception) {
            log.warn("No se pudo contactar a Brevo para={}: {}", destination.masked(),
                    String.valueOf(exception.getMessage()).replace(destination.getValue(), destination.masked()));
            throw new EmailDeliveryFailedException();
        }
    }

    private Map<String, Object> requestBody(EmailAddress destination, String message) {
        return Map.of(
                "sender", Map.of("name", properties.senderName(), "email", properties.senderAddress()),
                "to", List.of(Map.of("email", destination.getValue())),
                "subject", SUBJECT,
                "textContent", message);
    }
}
