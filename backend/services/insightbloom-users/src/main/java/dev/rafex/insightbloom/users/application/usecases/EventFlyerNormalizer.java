package dev.rafex.insightbloom.users.application.usecases;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Valida y normaliza flyers sin imponer el tamaño reducido de una foto de perfil. */
final class EventFlyerNormalizer {
    private static final Pattern DATA_URL = Pattern.compile(
            "^data:(image/png|image/jpeg);base64,([A-Za-z0-9+/=]+)$", Pattern.CASE_INSENSITIVE);
    private static final int MAX_DATA_URL_CHARS = 11_500_000;
    private static final int MAX_INPUT_BYTES = 8 * 1024 * 1024;
    private static final int MAX_DIMENSION = 4096;
    private static final int MAX_OUTPUT_DIMENSION = 2048;

    private EventFlyerNormalizer() {}

    static String normalize(final String dataUrl) {
        if (dataUrl == null || dataUrl.isBlank()) return null;
        final String trimmed = dataUrl.trim();
        if (trimmed.length() > MAX_DATA_URL_CHARS) throw new IllegalArgumentException("flyer_too_large");
        final Matcher matcher = DATA_URL.matcher(trimmed);
        if (!matcher.matches()) throw new IllegalArgumentException("flyer_format_not_allowed");
        final byte[] input;
        try {
            input = Base64.getDecoder().decode(matcher.group(2));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("flyer_invalid_base64");
        }
        if (input.length > MAX_INPUT_BYTES) throw new IllegalArgumentException("flyer_too_large");
        try (ImageInputStream imageInput = ImageIO.createImageInputStream(new ByteArrayInputStream(input))) {
            if (imageInput == null) throw new IllegalArgumentException("flyer_invalid_image");
            final Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) throw new IllegalArgumentException("flyer_invalid_image");
            final ImageReader reader = readers.next();
            try {
                reader.setInput(imageInput, true, true);
                final int width = reader.getWidth(0);
                final int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width > MAX_DIMENSION || height > MAX_DIMENSION) {
                    throw new IllegalArgumentException("flyer_dimensions_not_allowed");
                }
                final BufferedImage source = reader.read(0);
                if (source == null) throw new IllegalArgumentException("flyer_invalid_image");
                return encodeJpeg(resize(source));
            } finally {
                reader.dispose();
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (IOException e) {
            throw new IllegalArgumentException("flyer_invalid_image");
        }
    }

    private static BufferedImage resize(final BufferedImage source) {
        final double scale = Math.min(1d, MAX_OUTPUT_DIMENSION
                / (double) Math.max(source.getWidth(), source.getHeight()));
        final int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        final int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
        final BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        final Graphics2D graphics = target.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    private static String encodeJpeg(final BufferedImage image) throws IOException {
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "jpeg", output)) throw new IOException("jpeg_encoder_unavailable");
        return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(output.toByteArray());
    }
}
