package dev.rafex.insightbloom.users.adapters.outbound.etherpadclient;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class HttpEtherpadPortTest {

    @Test
    void sanitizeUrlForLogging_masksSensitiveApiKey() {
        // Arrange
        String urlWithKey = "https://etherpad-server:9001/api/1/createPad?apikey=SECRET_KEY_12345&padID=conference-123";

        // Act
        String sanitized = HttpEtherpadPort.sanitizeUrlForLogging(urlWithKey);

        // Assert
        assertEquals("https://etherpad-server:9001/api/1/createPad?apikey=***&padID=conference-123", sanitized);
    }

    @Test
    void sanitizeUrlForLogging_preservesOtherParameters() {
        // Arrange
        String urlWithMultipleParams = "https://etherpad-server:9001/api/1/getText?apikey=SECRET123&padID=conf-abc&revision=5";

        // Act
        String sanitized = HttpEtherpadPort.sanitizeUrlForLogging(urlWithMultipleParams);

        // Assert
        assertEquals("https://etherpad-server:9001/api/1/getText?apikey=***&padID=conf-abc&revision=5", sanitized);
    }

    @Test
    void sanitizeUrlForLogging_handlesTrailingApikey() {
        // Arrange
        String urlWithTrailingKey = "https://etherpad-server:9001/api/1/listAllPads?apikey=TRAILING_KEY";

        // Act
        String sanitized = HttpEtherpadPort.sanitizeUrlForLogging(urlWithTrailingKey);

        // Assert
        assertEquals("https://etherpad-server:9001/api/1/listAllPads?apikey=***", sanitized);
    }

    @Test
    void sanitizeUrlForLogging_doesNotModifyUrlWithoutApikey() {
        // Arrange
        String urlWithoutKey = "https://etherpad-server:9001/api/1/getText?padID=conference-123";

        // Act
        String sanitized = HttpEtherpadPort.sanitizeUrlForLogging(urlWithoutKey);

        // Assert
        assertEquals("https://etherpad-server:9001/api/1/getText?padID=conference-123", sanitized);
    }

    @Test
    void sanitizeUrlForLogging_handlesEmptyString() {
        // Arrange
        String emptyUrl = "";

        // Act
        String sanitized = HttpEtherpadPort.sanitizeUrlForLogging(emptyUrl);

        // Assert
        assertEquals("", sanitized);
    }

    @Test
    void sanitizeUrlForLogging_handlesNull() {
        // Arrange & Act
        String sanitized = HttpEtherpadPort.sanitizeUrlForLogging(null);

        // Assert
        assertNull(sanitized);
    }

    @Test
    void sanitizeUrlForLogging_multipleApikeyParams_masksAll() {
        // Arrange (edge case: multiple apikey params, which shouldn't happen but we handle it)
        String urlWithMultipleKeys = "https://etherpad-server:9001/api/1/test?apikey=KEY1&other=value&apikey=KEY2";

        // Act
        String sanitized = HttpEtherpadPort.sanitizeUrlForLogging(urlWithMultipleKeys);

        // Assert
        assertEquals("https://etherpad-server:9001/api/1/test?apikey=***&other=value&apikey=***", sanitized);
    }
}
