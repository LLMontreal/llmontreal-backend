package br.com.montreal.ai.llmontreal.service.extraction;

import br.com.montreal.ai.llmontreal.exception.ExtractionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@DisplayName("ZipContentExtractor Unit Tests")
class ZipContentExtractorTest {

    private ZipContentExtractor extractor;

    @Mock
    private ContentExtractor mockTextExtractor;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        when(mockTextExtractor.supportsThisContentType(anyString())).thenAnswer(invocation -> {
            String contentType = invocation.getArgument(0);
            return "text/plain".equals(contentType);
        });
        
        List<ContentExtractor> extractors = Arrays.asList(mockTextExtractor);
        extractor = new ZipContentExtractor(extractors);
    }

    @Test
    @DisplayName("Should support application/zip content type")
    void shouldSupportZipContentType() {
        boolean supports = extractor.supportsThisContentType("application/zip");
        assertThat(supports).isTrue();
    }

    @Test
    @DisplayName("Should not support other content types")
    void shouldNotSupportOtherContentTypes() {
        assertThat(extractor.supportsThisContentType("application/pdf")).isFalse();
        assertThat(extractor.supportsThisContentType("image/jpeg")).isFalse();
        assertThat(extractor.supportsThisContentType("text/plain")).isFalse();
        assertThat(extractor.supportsThisContentType(null)).isFalse();
    }

    @Test
    @DisplayName("Should have correct priority")
    void shouldHaveCorrectPriority() {
        int priority = extractor.getPriority();
        assertThat(priority).isEqualTo(20);
    }

    @Test
    @DisplayName("Should extract content from ZIP with text files")
    void shouldExtractContentFromZipWithTextFiles() throws Exception {
        byte[] zipData = createZipWithTextFiles();
        InputStream inputStream = new ByteArrayInputStream(zipData);

        when(mockTextExtractor.extractContent(any(InputStream.class), anyString()))
                .thenReturn("Extracted text content");

        String result = extractor.extractContent(inputStream, "application/zip");

        assertThat(result).isNotEmpty();
        assertThat(result).contains("FILE:");
        assertThat(result).contains("test.txt");
    }

    @Test
    @DisplayName("Should skip empty files in ZIP")
    void shouldSkipEmptyFilesInZip() throws Exception {
        byte[] zipData = createZipWithEmptyFile();
        InputStream inputStream = new ByteArrayInputStream(zipData);

        String result = extractor.extractContent(inputStream, "application/zip");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should skip system files in ZIP")
    void shouldSkipSystemFilesInZip() throws Exception {
        byte[] zipData = createZipWithSystemFiles();
        InputStream inputStream = new ByteArrayInputStream(zipData);

        String result = extractor.extractContent(inputStream, "application/zip");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should skip directories in ZIP")
    void shouldSkipDirectoriesInZip() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry dirEntry = new ZipEntry("folder/");
            zos.putNextEntry(dirEntry);
            zos.closeEntry();
        }

        byte[] zipData = baos.toByteArray();
        InputStream inputStream = new ByteArrayInputStream(zipData);

        String result = extractor.extractContent(inputStream, "application/zip");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should throw exception for unsupported content type")
    void shouldThrowExceptionForUnsupportedContentType() {
        InputStream inputStream = new ByteArrayInputStream("test".getBytes());

        assertThatThrownBy(() -> extractor.extractContent(inputStream, "application/pdf"))
                .isInstanceOf(ExtractionException.class)
                .hasMessageContaining("not supported");
    }

    @Test
    @DisplayName("Should handle ZIP with unsupported file types")
    void shouldHandleZipWithUnsupportedFileTypes() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry entry = new ZipEntry("image.jpg");
            zos.putNextEntry(entry);
            zos.write("fake image data".getBytes());
            zos.closeEntry();
        }

        byte[] zipData = baos.toByteArray();
        InputStream inputStream = new ByteArrayInputStream(zipData);

        String result = extractor.extractContent(inputStream, "application/zip");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should format extracted content with file names")
    void shouldFormatExtractedContentWithFileNames() throws Exception {
        byte[] zipData = createZipWithMultipleTextFiles();
        InputStream inputStream = new ByteArrayInputStream(zipData);

        when(mockTextExtractor.extractContent(any(InputStream.class), anyString()))
                .thenReturn("File content 1")
                .thenReturn("File content 2");

        String result = extractor.extractContent(inputStream, "application/zip");

        assertThat(result).contains("FILE: file1.txt");
        assertThat(result).contains("FILE: file2.txt");
        assertThat(result).contains("File content 1");
        assertThat(result).contains("File content 2");
    }

    private byte[] createZipWithTextFiles() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry entry = new ZipEntry("test.txt");
            zos.putNextEntry(entry);
            zos.write("This is test content".getBytes());
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    private byte[] createZipWithEmptyFile() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry entry = new ZipEntry("empty.txt");
            zos.putNextEntry(entry);
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    private byte[] createZipWithSystemFiles() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry macEntry = new ZipEntry("__MACOSX/file.txt");
            zos.putNextEntry(macEntry);
            zos.write("system file".getBytes());
            zos.closeEntry();

            ZipEntry hiddenEntry = new ZipEntry(".hidden");
            zos.putNextEntry(hiddenEntry);
            zos.write("hidden file".getBytes());
            zos.closeEntry();

            ZipEntry windowsEntry = new ZipEntry("Thumbs.db");
            zos.putNextEntry(windowsEntry);
            zos.write("thumbs".getBytes());
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    private byte[] createZipWithMultipleTextFiles() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry entry1 = new ZipEntry("file1.txt");
            zos.putNextEntry(entry1);
            zos.write("Content of file 1".getBytes());
            zos.closeEntry();

            ZipEntry entry2 = new ZipEntry("file2.txt");
            zos.putNextEntry(entry2);
            zos.write("Content of file 2".getBytes());
            zos.closeEntry();
        }
        return baos.toByteArray();
    }
}

