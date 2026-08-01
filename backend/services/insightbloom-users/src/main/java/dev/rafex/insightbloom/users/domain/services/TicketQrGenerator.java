package dev.rafex.insightbloom.users.domain.services;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Map;

/**
 * QR del lado servidor (2026-07-27), solo para el email de boleto -- la app web sigue
 * dibujando el QR en el navegador con la librería `qrcode` (TicketQr.vue). El correo
 * lo envía como recurso MIME inline para que también sobreviva al reenvío.
 */
public final class TicketQrGenerator {
    private static final int SIZE_PX = 260;

    private TicketQrGenerator() { }

    /** @return data URI {@code data:image/png;base64,...} lista para un <img src>. */
    public static String toPngDataUri(final String content) {
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(toPngBytes(content));
    }

    /** @return PNG del QR para adjuntarlo como recurso inline en un correo MIME. */
    public static byte[] toPngBytes(final String content) {
        try {
            final var hints = Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                    EncodeHintType.MARGIN, 1);
            final BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, SIZE_PX, SIZE_PX, hints);
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return out.toByteArray();
        } catch (final WriterException | java.io.IOException e) {
            throw new IllegalStateException("qr_generation_failed", e);
        }
    }
}
