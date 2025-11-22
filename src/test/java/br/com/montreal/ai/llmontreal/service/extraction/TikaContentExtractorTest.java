package br.com.montreal.ai.llmontreal.service.extraction;

import br.com.montreal.ai.llmontreal.exception.ExtractionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TikaContentExtractor Unit Tests")
class TikaContentExtractorTest {

    private TikaContentExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new TikaContentExtractor();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "text/plain"
    })
    @DisplayName("Should support correct content types")
    void shouldSupportCorrectContentTypes(String contentType) {
        boolean supports = extractor.supportsThisContentType(contentType);
        assertThat(supports).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "image/jpeg",
            "image/png",
            "video/mp4",
            "application/json",
            "text/html"
    })
    @DisplayName("Should not support incorrect content types")
    void shouldNotSupportIncorrectContentTypes(String contentType) {
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
    @DisplayName("Should extract text from plain text file")
    void shouldExtractTextFromPlainTextFile() throws Exception {
        String testContent = "Este é um teste de conteúdo em texto puro.";
        InputStream inputStream = new ByteArrayInputStream(testContent.getBytes());

        String result = extractor.extractContent(inputStream, "text/plain");

        assertThat(result).isNotEmpty();
        assertThat(result).contains("teste");
    }

    @Test
    @DisplayName("Should throw exception for unsupported content type")
    void shouldThrowExceptionForUnsupportedContentType() {
        InputStream inputStream = new ByteArrayInputStream("test".getBytes());

        assertThatThrownBy(() -> extractor.extractContent(inputStream, "image/jpeg"))
                .isInstanceOf(ExtractionException.class)
                .hasMessageContaining("not supported");
    }

    @Test
    @DisplayName("Should have correct priority")
    void shouldHaveCorrectPriority() {
        int priority = extractor.getPriority();
        assertThat(priority).isEqualTo(10);
    }
}

