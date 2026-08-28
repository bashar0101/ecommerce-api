package com.apps.ecommerce.service;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import lombok.extern.slf4j.Slf4j;

/**
 * Sends through Resend's HTTP API instead of SMTP.
 *
 * Render blocks outbound SMTP (25, 465, 587), and the block drops packets rather
 * than refusing them - so an SMTP send there does not fail, it hangs for the full
 * socket timeout and then reports "Connect timed out". Port 443 is never blocked,
 * so an HTTPS call sidesteps the problem entirely.
 *
 * It is also simply better feedback: a failure comes back as a status code and a
 * JSON body naming the reason, and a success returns a delivery id you can look
 * up in Resend's dashboard.
 */
@Service
@ConditionalOnProperty(name = "app.mail.provider", havingValue = "resend")
@Slf4j
public class ResendMailService implements MailService {

    private static final String ENDPOINT = "https://api.resend.com/emails";

    private final RestClient http;
    private final String apiKey;
    private final String from;

    public ResendMailService(
            @Value("${resend.api-key}") String apiKey,
            @Value("${app.mail.from}") String from,
            @Value("${resend.timeout-ms:10000}") long timeoutMs) {

        this.apiKey = apiKey;
        this.from = from;

        // Explicit timeouts: the default is no timeout at all, which would hand us
        // back the same indefinite hang we just moved away from.
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(timeoutMs));
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));

        this.http = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public void send(String to, String subject, String text) {
        Map<String, Object> body = Map.of(
                "from", from,
                "to", List.of(to),
                "subject", subject,
                "text", text);

        // Resend answers 200 with {"id": "..."}; anything else carries a JSON body
        // explaining why. Letting that surface beats a bare stack trace - a 401 is a
        // bad key, a 403 usually means the "from" address is not one you may use.
        Map<?, ?> response = http.post()
                .uri(ENDPOINT)
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);

        log.info("Mail sent to {} via Resend, id={}", to,
                response == null ? "unknown" : response.get("id"));
    }
}
