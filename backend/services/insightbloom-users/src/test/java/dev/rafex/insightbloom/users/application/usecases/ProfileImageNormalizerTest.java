package dev.rafex.insightbloom.users.application.usecases;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProfileImageNormalizerTest {
    @Test
    void normalizesPngToBoundedJpeg() throws Exception {
        final BufferedImage source = new BufferedImage(1200, 600, BufferedImage.TYPE_INT_ARGB);
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(source, "png", bytes);
        final String input = "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes.toByteArray());

        final String output = ProfileImageNormalizer.normalize(input);
        final byte[] jpeg = Base64.getDecoder().decode(output.substring(output.indexOf(',') + 1));
        final BufferedImage normalized = ImageIO.read(new java.io.ByteArrayInputStream(jpeg));

        assertEquals("data:image/jpeg", output.substring(0, output.indexOf(';')));
        assertEquals(512, normalized.getWidth());
        assertEquals(256, normalized.getHeight());
    }

    @Test
    void rejectsSvgDataUrls() {
        assertThrows(IllegalArgumentException.class,
                () -> ProfileImageNormalizer.normalize("data:image/svg+xml;base64,PHN2Zz48L3N2Zz4="));
    }
}
