package dev.rafex.insightbloom.users.adapters.inbound.http.handlers;

import dev.rafex.ether.http.jetty12.exchange.JettyHttpExchange;
import org.eclipse.jetty.server.Request;

import java.net.InetAddress;
import java.util.regex.Pattern;

/** Request metadata for OTP audit. HAProxy appends the connecting client as the last XFF hop. */
record OtpClientMetadata(String clientIp, String userAgent) {
    private static final Pattern IPV4_TEXT = Pattern.compile("[0-9]{1,3}(\\.[0-9]{1,3}){3}");
    private static final Pattern IPV6_TEXT = Pattern.compile("[0-9a-fA-F:.]+:[0-9a-fA-F:.]+");

    static OtpClientMetadata from(final JettyHttpExchange exchange) {
        final String forwarded = exchange.request().getHeaders().get("X-Forwarded-For");
        final String clientIp = lastForwardedAddress(forwarded)
                .orElseGet(() -> Request.getRemoteAddr(exchange.request()));
        return new OtpClientMetadata(clientIp, exchange.request().getHeaders().get("User-Agent"));
    }

    static java.util.Optional<String> lastForwardedAddress(final String forwardedFor) {
        if (forwardedFor == null || forwardedFor.isBlank()) return java.util.Optional.empty();
        final String[] hops = forwardedFor.split(",");
        // HAProxy appends the socket peer; earlier entries can be client-supplied and are ignored.
        for (int i = hops.length - 1; i >= 0; i--) {
            final String candidate = hops[i].trim();
            if (isIpLiteral(candidate)) return java.util.Optional.of(candidate);
        }
        return java.util.Optional.empty();
    }

    private static boolean isIpLiteral(final String value) {
        if (IPV4_TEXT.matcher(value).matches()) {
            try {
                final byte[] bytes = new byte[4];
                final String[] parts = value.split("\\.");
                for (int i = 0; i < parts.length; i++) {
                    final int octet = Integer.parseInt(parts[i]);
                    if (octet > 255) return false;
                    bytes[i] = (byte) octet;
                }
                return true;
            } catch (final NumberFormatException e) {
                return false;
            }
        }
        if (!IPV6_TEXT.matcher(value).matches()) return false;
        try {
            return InetAddress.getByName(value).getAddress().length == 16;
        } catch (final Exception e) {
            return false;
        }
    }
}
