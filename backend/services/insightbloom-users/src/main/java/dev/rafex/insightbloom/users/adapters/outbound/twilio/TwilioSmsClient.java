package dev.rafex.insightbloom.users.adapters.outbound.twilio;

import dev.rafex.insightbloom.users.domain.ports.SmsPort;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

public class TwilioSmsClient implements SmsPort {
    private final String accountSid;
    private final String authToken;
    private final String fromNumber;
    private final HttpClient httpClient;

    public TwilioSmsClient(final String accountSid, final String authToken, final String fromNumber) {
        this.accountSid = accountSid;
        this.authToken = authToken;
        this.fromNumber = fromNumber;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    @Override
    public boolean isEnabled() {
        return accountSid != null && !accountSid.isBlank()
                && authToken != null && !authToken.isBlank()
                && fromNumber != null && !fromNumber.isBlank();
    }

    @Override
    public void send(final String toPhone, final String message) {
        if (!isEnabled()) {
            throw new IllegalStateException("sms_provider_not_configured");
        }
        final String credentials = Base64.getEncoder()
                .encodeToString((accountSid + ":" + authToken).getBytes(StandardCharsets.UTF_8));
        final String form = "To=" + urlEncode(toPhone)
                + "&From=" + urlEncode(fromNumber)
                + "&Body=" + urlEncode(message);

        final HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.twilio.com/2010-04-01/Accounts/" + accountSid + "/Messages.json"))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Basic " + credentials)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();

        try {
            final HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new RuntimeException("twilio_send_failed: " + response.statusCode() + " " + response.body());
            }
        } catch (final java.io.IOException | InterruptedException e) {
            throw new RuntimeException("twilio_send_failed", e);
        }
    }

    private static String urlEncode(final String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
