package dev.rafex.insightbloom.toolsgateway;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class WebSocketProxyCreatorTest {

    @Test
    void negotiatesTtyForIdeRequests() {
        assertEquals("tty", WebSocketProxyCreator.acceptedSubprotocol(true, List.of("tty")));
    }

    @Test
    void doesNotNegotiateTtyForNonIdeRoutes() {
        assertNull(WebSocketProxyCreator.acceptedSubprotocol(false, List.of("tty")));
    }

    @Test
    void doesNotNegotiateUnsupportedProtocols() {
        assertNull(WebSocketProxyCreator.acceptedSubprotocol(true, List.of("other")));
    }
}
