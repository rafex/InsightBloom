package dev.rafex.insightbloom.users.domain.services;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class ImageNormalizerTest {
    private static final ImageNormalizer.Options OPTS = new ImageNormalizer.Options(
            11_500_000, 8 * 1024 * 1024, 4096, 200, false, "test");
    private static final ImageNormalizer.Options OPTS_PRESERVE_ALPHA = new ImageNormalizer.Options(
            11_500_000, 8 * 1024 * 1024, 4096, 200, true, "test");

    @Test
    void normalize_resizesLargeJpegAndKeepsJpeg() throws Exception {
        final String dataUrl = jpegDataUrl(800, 600);
        final String normalized = ImageNormalizer.normalize(dataUrl, OPTS);
        assertTrue(normalized.startsWith("data:image/jpeg;base64,"));
        final BufferedImage decoded = decode(normalized);
        assertTrue(Math.max(decoded.getWidth(), decoded.getHeight()) <= 200);
    }

    @Test
    void normalize_pngWithoutPreserveTransparency_flattensToJpeg() throws Exception {
        final String dataUrl = pngDataUrlWithAlpha(300, 300);
        final String normalized = ImageNormalizer.normalize(dataUrl, OPTS);
        assertTrue(normalized.startsWith("data:image/jpeg;base64,"));
    }

    @Test
    void normalize_pngWithPreserveTransparency_keepsPng() throws Exception {
        final String dataUrl = pngDataUrlWithAlpha(300, 300);
        final String normalized = ImageNormalizer.normalize(dataUrl, OPTS_PRESERVE_ALPHA);
        assertTrue(normalized.startsWith("data:image/png;base64,"));
        final BufferedImage decoded = decode(normalized);
        assertTrue(decoded.getColorModel().hasAlpha());
    }

    @Test
    void normalize_opaquePngWithPreserveTransparency_stillFlattensToJpeg() throws Exception {
        // Sin canal alfa real no hay nada que preservar -- se recomprime a JPEG igual (más liviano).
        final String dataUrl = jpegLikePngDataUrl(300, 300);
        final String normalized = ImageNormalizer.normalize(dataUrl, OPTS_PRESERVE_ALPHA);
        assertTrue(normalized.startsWith("data:image/jpeg;base64,"));
    }

    @Test
    void normalize_smallImageBelowCap_isNotUpscaled() throws Exception {
        final String dataUrl = jpegDataUrl(50, 50);
        final String normalized = ImageNormalizer.normalize(dataUrl, OPTS);
        final BufferedImage decoded = decode(normalized);
        assertEquals(50, decoded.getWidth());
        assertEquals(50, decoded.getHeight());
    }

    @Test
    void normalize_rejectsUnsupportedFormat() {
        final String dataUrl = "data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///ywAAAAAAQABAAACAUwAOw==";
        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> ImageNormalizer.normalize(dataUrl, OPTS));
        assertEquals("test_format_not_allowed", ex.getMessage());
    }

    @Test
    void normalize_rejectsOversizedInput() throws Exception {
        final String dataUrl = jpegDataUrl(4000, 4000);
        final ImageNormalizer.Options tinyLimit = new ImageNormalizer.Options(
                11_500_000, 1024, 4096, 200, false, "test");
        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> ImageNormalizer.normalize(dataUrl, tinyLimit));
        assertEquals("test_too_large", ex.getMessage());
    }

    @Test
    void normalize_rejectsDimensionsAboveMax() throws Exception {
        final String dataUrl = jpegDataUrl(5000, 100);
        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> ImageNormalizer.normalize(dataUrl, OPTS));
        assertEquals("test_dimensions_not_allowed", ex.getMessage());
    }

    @Test
    void normalize_nullOrBlank_returnsNull() {
        assertNull(ImageNormalizer.normalize(null, OPTS));
        assertNull(ImageNormalizer.normalize("  ", OPTS));
    }

    @Test
    void normalizeIfSupported_passesThroughUnsupportedFormatUnchanged() {
        final String dataUrl = "data:image/svg+xml;base64,PHN2Zy8+";
        assertEquals(dataUrl, ImageNormalizer.normalizeIfSupported(dataUrl, OPTS));
    }

    @Test
    void normalizeIfSupported_stillNormalizesPngAndJpeg() throws Exception {
        final String dataUrl = jpegDataUrl(800, 600);
        final String normalized = ImageNormalizer.normalizeIfSupported(dataUrl, OPTS);
        assertTrue(normalized.startsWith("data:image/jpeg;base64,"));
        final BufferedImage decoded = decode(normalized);
        assertTrue(Math.max(decoded.getWidth(), decoded.getHeight()) <= 200);
    }

    private static String jpegDataUrl(final int width, final int height) throws Exception {
        final BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        final Graphics2D g = image.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, width, height);
        g.dispose();
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpeg", out);
        return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
    }

    private static String pngDataUrlWithAlpha(final int width, final int height) throws Exception {
        final BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D g = image.createGraphics();
        g.setColor(new Color(255, 0, 0, 128));
        g.fillRect(0, 0, width, height);
        g.dispose();
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
    }

    private static String jpegLikePngDataUrl(final int width, final int height) throws Exception {
        final BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        final Graphics2D g = image.createGraphics();
        g.setColor(Color.GREEN);
        g.fillRect(0, 0, width, height);
        g.dispose();
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
    }

    private static BufferedImage decode(final String dataUrl) throws Exception {
        final String base64 = dataUrl.substring(dataUrl.indexOf(",") + 1);
        return ImageIO.read(new java.io.ByteArrayInputStream(Base64.getDecoder().decode(base64)));
    }
}
