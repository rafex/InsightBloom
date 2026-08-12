package dev.rafex.insightbloom.users.domain.services;

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

/**
 * Redimensiona y recomprime imágenes subidas por el usuario (flyer, logo/fondo de
 * certificado) antes de guardarlas, para no depender de que el cliente comprima bien.
 * Solo procesa PNG/JPEG -- otros formatos ya permitidos aguas arriba (gif/webp/svg en
 * el editor de certificados) se dejan pasar sin tocar vía {@link #normalizeIfSupported}.
 */
public final class ImageNormalizer {
    private static final Pattern DATA_URL = Pattern.compile(
            "^data:(image/png|image/jpeg);base64,([A-Za-z0-9+/=]+)$", Pattern.CASE_INSENSITIVE);

    private ImageNormalizer() {}

    /**
     * @param preserveTransparency si la fuente es PNG con canal alfa, mantiene PNG con
     *                             fondo transparente en vez de aplanar a JPEG con fondo blanco
     *                             (necesario para el logotipo del certificado, que se superpone
     *                             sobre un fondo de color).
     */
    public record Options(int maxDataUrlChars, int maxInputBytes, int maxDimension,
                           int maxOutputDimension, boolean preserveTransparency, String errorPrefix) {}

    /** Igual que {@link #normalize} pero devuelve el original sin tocar si el formato no es
     * PNG/JPEG, en vez de fallar -- para no romper imágenes en formatos que otra capa ya validó. */
    public static String normalizeIfSupported(final String dataUrl, final Options options) {
        if (dataUrl == null || dataUrl.isBlank()) return dataUrl;
        if (!DATA_URL.matcher(dataUrl.trim()).matches()) return dataUrl;
        return normalize(dataUrl, options);
    }

    public static String normalize(final String dataUrl, final Options options) {
        if (dataUrl == null || dataUrl.isBlank()) return null;
        final String trimmed = dataUrl.trim();
        if (trimmed.length() > options.maxDataUrlChars()) {
            throw new IllegalArgumentException(options.errorPrefix() + "_too_large");
        }
        final Matcher matcher = DATA_URL.matcher(trimmed);
        if (!matcher.matches()) throw new IllegalArgumentException(options.errorPrefix() + "_format_not_allowed");
        final boolean sourceIsPng = matcher.group(1).equalsIgnoreCase("image/png");
        final byte[] input;
        try {
            input = Base64.getDecoder().decode(matcher.group(2));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(options.errorPrefix() + "_invalid_base64");
        }
        if (input.length > options.maxInputBytes()) {
            throw new IllegalArgumentException(options.errorPrefix() + "_too_large");
        }
        try (ImageInputStream imageInput = ImageIO.createImageInputStream(new ByteArrayInputStream(input))) {
            if (imageInput == null) throw new IllegalArgumentException(options.errorPrefix() + "_invalid_image");
            final Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) throw new IllegalArgumentException(options.errorPrefix() + "_invalid_image");
            final ImageReader reader = readers.next();
            try {
                reader.setInput(imageInput, true, true);
                final int width = reader.getWidth(0);
                final int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width > options.maxDimension() || height > options.maxDimension()) {
                    throw new IllegalArgumentException(options.errorPrefix() + "_dimensions_not_allowed");
                }
                final BufferedImage source = reader.read(0);
                if (source == null) throw new IllegalArgumentException(options.errorPrefix() + "_invalid_image");
                final boolean keepAlpha = options.preserveTransparency() && sourceIsPng && source.getColorModel().hasAlpha();
                final BufferedImage resized = resize(source, options.maxOutputDimension(), keepAlpha);
                return keepAlpha ? encode(resized, "png", "image/png") : encode(resized, "jpeg", "image/jpeg");
            } finally {
                reader.dispose();
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (IOException e) {
            throw new IllegalArgumentException(options.errorPrefix() + "_invalid_image");
        }
    }

    private static BufferedImage resize(final BufferedImage source, final int maxOutputDimension, final boolean keepAlpha) {
        final double scale = Math.min(1d, maxOutputDimension / (double) Math.max(source.getWidth(), source.getHeight()));
        final int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        final int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
        final BufferedImage target = new BufferedImage(width, height,
                keepAlpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        final Graphics2D graphics = target.createGraphics();
        try {
            if (!keepAlpha) {
                graphics.setColor(Color.WHITE);
                graphics.fillRect(0, 0, width, height);
            }
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    private static String encode(final BufferedImage image, final String formatName, final String mimeType) {
        try {
            final ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (!ImageIO.write(image, formatName, output)) throw new IOException(formatName + "_encoder_unavailable");
            return "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (IOException e) {
            throw new IllegalArgumentException("image_encode_failed");
        }
    }
}
