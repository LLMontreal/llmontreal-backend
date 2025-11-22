package br.com.montreal.ai.llmontreal.service.extraction;

import br.com.montreal.ai.llmontreal.exception.ExtractionException;
import net.sourceforge.tess4j.TesseractException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TesseractContentExtractor Unit Tests")
class TesseractContentExtractorTest {

    private TesseractContentExtractor extractor;

    @BeforeEach
    void setUp() throws TesseractException {
        extractor = new TesseractContentExtractor(
                "",
                "eng", // apenas inglês para testes mais rápidos
                3,
                3
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/tiff",
            "image/tif",
            "image/bmp",
            "image/gif"
    })
    @DisplayName("Should support correct image content types")
    void shouldSupportCorrectContentTypes(String contentType) {
        boolean supports = extractor.supportsThisContentType(contentType);
        assertThat(supports).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "text/plain",
            "application/json",
            "video/mp4",
            "audio/mp3"
    })
    @DisplayName("Should not support non-image content types")
    void shouldNotSupportNonImageContentTypes(String contentType) {
        boolean supports = extractor.supportsThisContentType(contentType);
        assertThat(supports).isFalse();
    }

    @Test
    @DisplayName("Should not support null content type")
    void shouldNotSupportNullContentType() {
        boolean supports = extractor.supportsThisContentType(null);
        assertThat(supports).isFalse();
    }

    @Test
    @DisplayName("Should throw exception for unsupported content type")
    void shouldThrowExceptionForUnsupportedContentType() throws Exception {
        BufferedImage image = createSimpleTestImage("TEST");
        InputStream inputStream = imageToInputStream(image, "png");

        assertThatThrownBy(() -> extractor.extractContent(inputStream, "application/pdf"))
                .isInstanceOf(ExtractionException.class)
                .hasMessageContaining("not supported");
    }

    @Test
    @DisplayName("Should throw exception for corrupted image stream")
    void shouldThrowExceptionForCorruptedImageStream() {
        byte[] invalidData = "This is not an image".getBytes();
        InputStream inputStream = new ByteArrayInputStream(invalidData);

        assertThatThrownBy(() -> extractor.extractContent(inputStream, "image/png"))
                .isInstanceOf(ExtractionException.class);
    }

    @Test
    @DisplayName("Should handle case-insensitive content types")
    void shouldHandleCaseInsensitiveContentTypes() {
        assertThat(extractor.supportsThisContentType("IMAGE/JPEG")).isTrue();
        assertThat(extractor.supportsThisContentType("Image/Png")).isTrue();
        assertThat(extractor.supportsThisContentType("image/TIFF")).isTrue();
    }

    @Test
    @DisplayName("Should support content type with charset")
    void shouldSupportContentTypeWithCharset() {
        assertThat(extractor.supportsThisContentType("image/jpeg; charset=utf-8")).isTrue();
        assertThat(extractor.supportsThisContentType("image/png; quality=high")).isTrue();
    }

    private BufferedImage createSimpleTestImage(String text) {
        int width = 400;
        int height = 100;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();

        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);

        g.setColor(Color.BLACK);
        g.setFont(new Font("Arial", Font.BOLD, 48));

        FontMetrics fm = g.getFontMetrics();
        int textWidth = fm.stringWidth(text);
        int x = (width - textWidth) / 2;
        int y = ((height - fm.getHeight()) / 2) + fm.getAscent();

        g.drawString(text, x, y);
        g.dispose();

        return image;
    }

    private InputStream imageToInputStream(BufferedImage image, String format) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, format, baos);
        return new ByteArrayInputStream(baos.toByteArray());
    }
}

