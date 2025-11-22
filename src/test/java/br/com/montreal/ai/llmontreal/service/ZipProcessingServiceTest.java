package br.com.montreal.ai.llmontreal.service;

import br.com.montreal.ai.llmontreal.entity.Document;
import br.com.montreal.ai.llmontreal.entity.enums.DocumentStatus;
import br.com.montreal.ai.llmontreal.repository.DocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@DisplayName("ZipProcessingService Unit Tests")
class ZipProcessingServiceTest {

    private ZipProcessingService zipProcessingService;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentExtractionService extractionService;

    private final AtomicLong idGenerator = new AtomicLong(1);

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        zipProcessingService = new ZipProcessingService(documentRepository, extractionService);
        
        ReflectionTestUtils.setField(zipProcessingService, "maxEntrySize", 104857600L);
        
        idGenerator.set(1);
    }

    @Test
    @DisplayName("Should create separate documents for each file in ZIP")
    void shouldCreateSeparateDocuments() throws Exception {
        byte[] zipData = createZipWithMultipleFiles();
        setupDocumentSaveMock();

        List<Long> documentIds = zipProcessingService.processZipFile(zipData, "test.zip");

        assertThat(documentIds)
                .hasSize(2)
                .containsExactly(1L, 2L);
        
        verify(documentRepository, times(2)).save(any(Document.class));
        verify(extractionService, times(2)).extractContentAsync(anyLong());
        verify(extractionService).extractContentAsync(1L);
        verify(extractionService).extractContentAsync(2L);
    }

    @Test
    @DisplayName("Should skip system files and directories")
    void shouldSkipSystemFiles() throws Exception {
        byte[] zipData = createZipWithSystemFiles();
        
        List<Long> documentIds = zipProcessingService.processZipFile(zipData, "test.zip");

        assertThat(documentIds).isEmpty();
        verify(documentRepository, never()).save(any(Document.class));
    }

    @Test
    @DisplayName("Should set correct file names from ZIP entries")
    void shouldSetCorrectFileNames() throws Exception {
        byte[] zipData = createZipWithMultipleFiles();
        ArgumentCaptor<Document> documentCaptor = ArgumentCaptor.forClass(Document.class);
        setupDocumentSaveMock();

        zipProcessingService.processZipFile(zipData, "documents.zip");

        verify(documentRepository, times(2)).save(documentCaptor.capture());
        List<Document> savedDocuments = documentCaptor.getAllValues();
        
        assertThat(savedDocuments).hasSize(2);
        assertThat(savedDocuments.get(0).getFileName())
                .contains("documents/")
                .contains("file1.txt");
        assertThat(savedDocuments.get(1).getFileName())
                .contains("documents/")
                .contains("file2.txt");
    }

    @Test
    @DisplayName("Should detect correct content types")
    void shouldDetectContentTypes() throws Exception {
        byte[] zipData = createZipWithMultipleFiles();
        ArgumentCaptor<Document> documentCaptor = ArgumentCaptor.forClass(Document.class);
        setupDocumentSaveMock();

        zipProcessingService.processZipFile(zipData, "test.zip");

        verify(documentRepository, times(2)).save(documentCaptor.capture());
        List<Document> savedDocuments = documentCaptor.getAllValues();
        
        assertThat(savedDocuments)
                .hasSize(2)
                .allMatch(doc -> doc.getFileType() != null && !doc.getFileType().isEmpty())
                .allMatch(doc -> "text/plain".equals(doc.getFileType()));
    }

    @Test
    @DisplayName("Should set documents status to PENDING")
    void shouldSetStatusToPending() throws Exception {
        byte[] zipData = createZipWithMultipleFiles();
        ArgumentCaptor<Document> documentCaptor = ArgumentCaptor.forClass(Document.class);
        setupDocumentSaveMock();

        zipProcessingService.processZipFile(zipData, "test.zip");

        verify(documentRepository, times(2)).save(documentCaptor.capture());
        List<Document> savedDocuments = documentCaptor.getAllValues();
        
        assertThat(savedDocuments)
                .allMatch(doc -> doc.getStatus() == DocumentStatus.PENDING)
                .allMatch(doc -> doc.getCreatedAt() != null)
                .allMatch(doc -> doc.getUpdatedAt() != null);
    }

    @Test
    @DisplayName("Should handle empty files correctly")
    void shouldHandleEmptyFiles() throws Exception {
        byte[] zipData = createZipWithEmptyFile();

        List<Long> documentIds = zipProcessingService.processZipFile(zipData, "test.zip");

        assertThat(documentIds).isEmpty();
        verify(documentRepository, never()).save(any(Document.class));
        verify(extractionService, never()).extractContentAsync(anyLong());
    }

    @Test
    @DisplayName("Should skip unsupported file types")
    void shouldSkipUnsupportedFileTypes() throws Exception {
        byte[] zipData = createZipWithUnsupportedFiles();
        setupDocumentSaveMock();

        List<Long> documentIds = zipProcessingService.processZipFile(zipData, "test.zip");

        assertThat(documentIds).hasSize(0);
    }

    @Test
    @DisplayName("Should handle mixed valid and invalid files")
    void shouldHandleMixedFiles() throws Exception {
        byte[] zipData = createZipWithMixedFiles();
        setupDocumentSaveMock();

        List<Long> documentIds = zipProcessingService.processZipFile(zipData, "test.zip");

        assertThat(documentIds).hasSize(1);
        verify(documentRepository, times(1)).save(any(Document.class));
        verify(extractionService, times(1)).extractContentAsync(anyLong());
    }

    private void setupDocumentSaveMock() {
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> {
            Document doc = invocation.getArgument(0);
            doc.setId(idGenerator.getAndIncrement());
            return doc;
        });
    }

    private byte[] createZipWithMultipleFiles() throws Exception {
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

    private byte[] createZipWithSystemFiles() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry dirEntry = new ZipEntry("folder/");
            zos.putNextEntry(dirEntry);
            zos.closeEntry();

            ZipEntry macEntry = new ZipEntry("__MACOSX/file.txt");
            zos.putNextEntry(macEntry);
            zos.write("system file".getBytes());
            zos.closeEntry();

            ZipEntry hiddenEntry = new ZipEntry(".hidden");
            zos.putNextEntry(hiddenEntry);
            zos.write("hidden file".getBytes());
            zos.closeEntry();

            ZipEntry thumbsEntry = new ZipEntry("Thumbs.db");
            zos.putNextEntry(thumbsEntry);
            zos.write("thumbs data".getBytes());
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

    private byte[] createZipWithUnsupportedFiles() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            ByteArrayOutputStream nestedBaos = new ByteArrayOutputStream();
            try (ZipOutputStream nestedZos = new ZipOutputStream(nestedBaos)) {
                ZipEntry nestedEntry = new ZipEntry("internal.txt");
                nestedZos.putNextEntry(nestedEntry);
                nestedZos.write("nested content".getBytes());
                nestedZos.closeEntry();
            }
            
            ZipEntry zipEntry = new ZipEntry("nested.zip");
            zos.putNextEntry(zipEntry);
            zos.write(nestedBaos.toByteArray());
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    private byte[] createZipWithMixedFiles() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry validEntry = new ZipEntry("valid.txt");
            zos.putNextEntry(validEntry);
            zos.write("Valid text content".getBytes());
            zos.closeEntry();

            ZipEntry dirEntry = new ZipEntry("folder/");
            zos.putNextEntry(dirEntry);
            zos.closeEntry();

            ZipEntry systemEntry = new ZipEntry("__MACOSX/resource");
            zos.putNextEntry(systemEntry);
            zos.write("system data".getBytes());
            zos.closeEntry();

            ZipEntry emptyEntry = new ZipEntry("empty.txt");
            zos.putNextEntry(emptyEntry);
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

}

