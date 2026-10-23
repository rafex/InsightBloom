package dev.rafex.insightbloom.users.domain.services;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Deterministic COSE_Sign1 profile used by organizer-issued tickets (EdDSA / Ed25519). */
public final class CoseSign1Ed25519 {
    private static final byte[] PROTECTED = new byte[] {(byte) 0xa1, 0x01, 0x27}; // {1: -8}
    private CoseSign1Ed25519() {}

    public record Parts(byte[] keyId, byte[] payload, byte[] signature) {}

    public static byte[] signatureInput(final byte[] payload) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0x84); text(out, "Signature1"); bytes(out, PROTECTED); bytes(out, new byte[0]); bytes(out, payload);
        return out.toByteArray();
    }

    public static byte[] encode(final byte[] keyId, final byte[] payload, final byte[] signature) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0x84); bytes(out, PROTECTED); out.write(0xa1); out.write(0x04); bytes(out, keyId);
        bytes(out, payload); bytes(out, signature);
        return out.toByteArray();
    }

    /** Parses only the canonical profile above; unknown headers, tags and noncanonical lengths fail closed. */
    public static Parts decode(final byte[] cose) {
        if (cose == null || cose.length < 16 || cose.length > 1_000_000) throw new IllegalArgumentException("cose_structure_invalid");
        final Reader reader = new Reader(cose);
        reader.expect(0x84);
        final byte[] protectedHeaders = reader.bytes();
        if (!Arrays.equals(PROTECTED, protectedHeaders)) throw new IllegalArgumentException("cose_algorithm_invalid");
        reader.expect(0xa1); reader.expect(0x04);
        final byte[] keyId = reader.bytes();
        final byte[] payload = reader.bytes();
        final byte[] signature = reader.bytes();
        if (signature.length != 64 || keyId.length == 0 || keyId.length > 128 || reader.hasRemaining())
            throw new IllegalArgumentException("cose_structure_invalid");
        if (!Arrays.equals(cose, encode(keyId, payload, signature))) throw new IllegalArgumentException("cose_noncanonical");
        return new Parts(keyId, payload, signature);
    }

    private static void text(final ByteArrayOutputStream out, final String value) {
        final byte[] encoded = value.getBytes(StandardCharsets.US_ASCII);
        writeLength(out, 0x60, encoded.length); out.writeBytes(encoded);
    }
    private static void bytes(final ByteArrayOutputStream out, final byte[] value) {
        writeLength(out, 0x40, value.length); out.writeBytes(value);
    }
    private static void writeLength(final ByteArrayOutputStream out, final int major, final int length) {
        if (length < 24) out.write(major | length);
        else if (length <= 255) { out.write(major | 24); out.write(length); }
        else if (length <= 65535) { out.write(major | 25); out.write(length >>> 8); out.write(length); }
        else { out.write(major | 26); out.write(length >>> 24); out.write(length >>> 16); out.write(length >>> 8); out.write(length); }
    }

    private static final class Reader {
        private final byte[] input; private int offset;
        private Reader(final byte[] input) { this.input = input; }
        private boolean hasRemaining() { return offset != input.length; }
        private void expect(final int expected) {
            if (offset >= input.length || (input[offset++] & 0xff) != expected) throw new IllegalArgumentException("cose_structure_invalid");
        }
        private byte[] bytes() {
            if (offset >= input.length) throw new IllegalArgumentException("cose_structure_invalid");
            final int first = input[offset++] & 0xff;
            if ((first & 0xe0) != 0x40) throw new IllegalArgumentException("cose_structure_invalid");
            final int ai = first & 0x1f;
            final int length;
            if (ai < 24) length = ai;
            else if (ai == 24) length = next(1);
            else if (ai == 25) length = next(2);
            else if (ai == 26) length = next(4);
            else throw new IllegalArgumentException("cose_structure_invalid");
            if (length < 0 || offset + length > input.length) throw new IllegalArgumentException("cose_structure_invalid");
            final byte[] value = Arrays.copyOfRange(input, offset, offset + length); offset += length;
            return value;
        }
        private int next(final int width) {
            if (offset + width > input.length) throw new IllegalArgumentException("cose_structure_invalid");
            long value = 0; for (int i = 0; i < width; i++) value = (value << 8) | (input[offset++] & 0xffL);
            if ((width == 1 && value < 24) || (width == 2 && value <= 255) || (width == 4 && value <= 65535) || value > Integer.MAX_VALUE)
                throw new IllegalArgumentException("cose_noncanonical");
            return (int) value;
        }
    }
}
