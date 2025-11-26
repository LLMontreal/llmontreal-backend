package br.com.montreal.ai.llmontreal.service;

import br.com.montreal.ai.llmontreal.dto.DocumentUploadResponse;
import br.com.montreal.ai.llmontreal.entity.Document;
import br.com.montreal.ai.llmontreal.entity.enums.DocumentStatus;
import br.com.montreal.ai.llmontreal.exception.FileUploadException;
import br.com.montreal.ai.llmontreal.exception.FileValidationException;
import br.com.montreal.ai.llmontreal.repository.DocumentRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.junit.jupiter.MockitoExtension;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DocumentService Unit Tests")
class DocumentServiceTests {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentExtractionService documentExtractionService;

    @Mock
    private ZipProcessingService zipProcessingService;

    @Mock
    private org.springframework.kafka.core.KafkaTemplate kafkaSummaryTemplate;

    @Mock
    private br.com.montreal.ai.llmontreal.service.ollama.OllamaProducerService ollamaProducerService;

    @InjectMocks
    private DocumentService documentService;

    private Pageable pageable;
    private List<Document> documentsList;

    private MockMultipartFile validFile;
    private Document savedDoc;

    @BeforeEach
    void setUp() {
        pageable = PageRequest.of(0, 3);
        documentsList = List.of(
                new Document(),
                new Document(),
                new Document(),
                new Document(),
                new Document(),
                new Document()
        );

        byte[] fileContent = "Conteúdo do documento".getBytes();
        validFile = new MockMultipartFile(
                "file",
                "documento-teste.pdf",
                "application/pdf",
                fileContent
        );

        savedDoc = Document.builder()
                .id(1L)
                .fileName("documento-teste.pdf")
                .fileType("application/pdf")
                .fileData(fileContent)
                .status(DocumentStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    void shouldReturnPage() {
        List<Document> docs = documentsList.subList(0, 3);
        Page<Document> expectedPage = new PageImpl<>(docs, pageable, 6);

        when(documentRepository.findAll(pageable)).thenReturn(expectedPage);

        Page<Document> result = documentService.getAllDocuments(pageable, null);

        assertThat(result).isNotNull();
        assertThat(result.isEmpty()).isFalse();
        assertThat(result.getContent()).hasSize(3);
        assertThat(result.getTotalElements()).isEqualTo(6);
        assertThat(result.getTotalPages()).isEqualTo(2);
        assertThat(result.getNumber()).isEqualTo(0);
        assertThat(result.hasContent()).isTrue();
        assertThat(result.hasNext()).isTrue();

        verify(documentRepository).findAll(pageable);
    }

    @ParameterizedTest
    @EnumSource(DocumentStatus.class)
    void shouldReturnDocumentsForEachStatus(DocumentStatus status) {
        List<Document> docsWithStatus = List.of(
                Document.builder()
                        .id(1L)
                        .fileName("doc1.pdf")
                        .status(status)
                        .build(),
                Document.builder()
                        .id(2L)
                        .fileName("doc2.pdf")
                        .status(status)
                        .build()
        );

        Page<Document> expectedPage = new PageImpl<>(docsWithStatus, pageable, 2);

        when(documentRepository.findAllByStatus(pageable, status))
                .thenReturn(expectedPage);

        Page<Document> result = documentService.getAllDocuments(pageable, status);

        assertThat(result.getContent())
                .isNotEmpty()
                .allSatisfy(doc -> assertThat(doc.getStatus()).isEqualTo(status));

        verify(documentRepository).findAllByStatus(pageable, status);
    }

    @Test
    void shouldReturnEmptyPageWhenNoDocumentsFoundByValidStatus() {
        when(documentRepository.findAllByStatus(eq(pageable), any(DocumentStatus.class))).thenReturn(Page.empty());

        Page<Document> result = documentService.getAllDocuments(pageable, DocumentStatus.COMPLETED);
        assertThat(result).isNotNull();
        assertThat(result.getContent()).isEmpty();
        verify(documentRepository).findAllByStatus(pageable, DocumentStatus.COMPLETED);
    }

    @Test
    @DisplayName("Should upload document successfully")
    void shouldUploadDocumentSuccessfully() throws Exception {
        Document extractedDoc = Document.builder()
                .id(1L)
                .fileName("documento-teste.pdf")
                .fileType("application/pdf")
                .fileData("Conteúdo do documento".getBytes())
                .status(DocumentStatus.COMPLETED)
                .extractedContent("Extracted content")
                .createdAt(LocalDateTime.now())
                .build();

        when(documentRepository.save(any(Document.class))).thenReturn(savedDoc);
        when(documentExtractionService.extractContentSync(savedDoc.getId())).thenReturn("Extracted content");
        when(documentRepository.findById(savedDoc.getId()))
                .thenReturn(java.util.Optional.of(extractedDoc))
                .thenReturn(java.util.Optional.of(extractedDoc));
        when(ollamaProducerService.sendSummarizeRequest(any(Document.class), any(String.class)))
                .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(
                        br.com.montreal.ai.llmontreal.dto.kafka.KafkaSummaryResponseDTO.builder().build()
                ));

        DocumentUploadResponse result = documentService.uploadFile(validFile, "test-correlation-id");

        assertNotNull(result);
        assertEquals("documento-teste.pdf", result.fileName());
        assertEquals("application/pdf", result.fileType());

        verify(documentRepository, atLeast(1)).save(any(Document.class));
        verify(documentExtractionService).extractContentSync(savedDoc.getId());
    }

    @Test
    @DisplayName("Should throw FileValidationException when file is null")
    void shouldThrowExceptionWhenFileIsNull() {
        assertThatThrownBy(() -> documentService.uploadFile(null, "test-correlation-id"))
                .isInstanceOf(FileValidationException.class)
                .hasMessage("O arquivo não pode ser nulo ou vazio");

        verify(documentRepository, never()).save(any(Document.class));
    }

    @Test
    @DisplayName("Should throw FileValidationException when file is empty")
    void shouldThrowExceptionWhenFileIsEmpty() {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file",
                "empty.pdf",
                "application/pdf",
                new byte[0]
        );

        assertThatThrownBy(() -> documentService.uploadFile(emptyFile, "test-correlation-id"))
                .isInstanceOf(FileValidationException.class)
                .hasMessage("O arquivo não pode ser nulo ou vazio");

        verify(documentRepository, never()).save(any(Document.class));
    }

    @Test
    @DisplayName("Should throw FileValidationException when file exceeds max size")
    void shouldThrowExceptionWhenFileTooLarge() {
        byte[] largeContent = new byte[26 * 1024 * 1024]; // 26 MB
        MockMultipartFile largeFile = new MockMultipartFile(
                "file",
                "large-file.pdf",
                "application/pdf",
                largeContent
        );

        assertThatThrownBy(() -> documentService.uploadFile(largeFile, "test-correlation-id"))
                .isInstanceOf(FileValidationException.class)
                .hasMessageContaining("excede o tamanho máximo permitido");

        verify(documentRepository, never()).save(any(Document.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"application/json", "video/mp4", "audio/mpeg", "text/html"})
    @DisplayName("Should throw FileValidationException for unsupported content types")
    void shouldThrowExceptionForUnsupportedContentType(String contentType) {
        MockMultipartFile invalidFile = new MockMultipartFile(
                "file",
                "file.txt",
                contentType,
                "Content".getBytes()
        );

        assertThatThrownBy(() -> documentService.uploadFile(invalidFile, "test-correlation-id"))
                .isInstanceOf(FileValidationException.class)
                .hasMessageContaining("Tipo de arquivo não suportado");

        verify(documentRepository, never()).save(any(Document.class));
        verify(documentExtractionService, never()).extractContentAsync(any(Long.class), any(String.class));
    }

    @Test
    @DisplayName("Should throw FileValidationException when file has no content type")
    void shouldThrowExceptionWhenFileHasNoContentType() {
        MockMultipartFile fileWithNoContentType = new MockMultipartFile(
                "file",
                "file.pdf",
                null,
                "Content".getBytes()
        );

        assertThatThrownBy(() -> documentService.uploadFile(fileWithNoContentType, "test-correlation-id"))
                .isInstanceOf(FileValidationException.class)
                .hasMessageContaining("Tipo de arquivo não suportado");

        verify(documentRepository, never()).save(any(Document.class));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "\t", "\n"})
    @DisplayName("Should throw FileValidationException when filename is invalid")
    void shouldThrowExceptionWhenFilenameIsInvalid(String invalidFilename) {
        MockMultipartFile fileWithInvalidName = new MockMultipartFile(
                "file",
                invalidFilename,
                "application/pdf",
                "Content".getBytes()
        );

        assertThatThrownBy(() -> documentService.uploadFile(fileWithInvalidName, "test-correlation-id"))
                .isInstanceOf(FileValidationException.class)
                .hasMessage("Nome do arquivo inválido");

        verify(documentRepository, never()).save(any(Document.class));
    }

    @Test
    @DisplayName("Should throw FileUploadException when IOException occurs")
    void shouldThrowFileUploadExceptionWhenIOErrorOccurs() throws IOException {
        MultipartFile mockFile = mock(MultipartFile.class);
        when(mockFile.isEmpty()).thenReturn(false);
        when(mockFile.getOriginalFilename()).thenReturn("test.pdf");
        when(mockFile.getContentType()).thenReturn("application/pdf");
        when(mockFile.getSize()).thenReturn(1024L);
        when(mockFile.getBytes()).thenThrow(new IOException("Erro ao ler arquivo"));

        assertThatThrownBy(() -> documentService.uploadFile(mockFile, "test-correlation-id"))
                .isInstanceOf(FileUploadException.class)
                .hasMessageContaining("Erro ao ler o conteúdo do arquivo")
                .hasCauseInstanceOf(IOException.class);

        verify(documentRepository, never()).save(any(Document.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"application/pdf", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "image/jpeg", "image/png", "text/plain"})
    @DisplayName("Should accept all supported file types")
    void shouldAcceptAllSupportedFileTypes(String contentType) throws Exception {
        byte[] fileContent = "Content".getBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "document." + getExtension(contentType),
                contentType,
                fileContent
        );

        Document doc = Document.builder()
                .id(1L)
                .fileName(file.getOriginalFilename())
                .fileType(contentType)
                .fileData(fileContent)
                .status(DocumentStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        Document extractedDoc = Document.builder()
                .id(1L)
                .fileName(file.getOriginalFilename())
                .fileType(contentType)
                .fileData(fileContent)
                .status(DocumentStatus.COMPLETED)
                .extractedContent("Extracted content")
                .createdAt(LocalDateTime.now())
                .build();

        when(documentRepository.save(any(Document.class))).thenReturn(doc);
        when(documentExtractionService.extractContentSync(doc.getId())).thenReturn("Extracted content");
        when(documentRepository.findById(doc.getId()))
                .thenReturn(java.util.Optional.of(extractedDoc))
                .thenReturn(java.util.Optional.of(extractedDoc));
        when(ollamaProducerService.sendSummarizeRequest(any(Document.class), any(String.class)))
                .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(
                        br.com.montreal.ai.llmontreal.dto.kafka.KafkaSummaryResponseDTO.builder().build()
                ));

        DocumentUploadResponse result = documentService.uploadFile(file, "test-correlation-id");

        assertNotNull(result);
        assertEquals(contentType, result.fileType());
        verify(documentRepository, atLeast(1)).save(any(Document.class));
        verify(documentExtractionService).extractContentSync(doc.getId());
    }

    private String getExtension(String contentType) {
        return switch (contentType) {
            case "application/pdf" -> "pdf";
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx";
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "text/plain" -> "txt";
            default -> "file";
        };
    }
}
