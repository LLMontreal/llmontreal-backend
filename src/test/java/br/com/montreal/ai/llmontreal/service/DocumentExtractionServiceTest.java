package br.com.montreal.ai.llmontreal.service;

import br.com.montreal.ai.llmontreal.entity.Document;
import br.com.montreal.ai.llmontreal.entity.enums.DocumentStatus;
import br.com.montreal.ai.llmontreal.event.DocumentExtractionCompletedEvent;
import br.com.montreal.ai.llmontreal.exception.ExtractionException;
import br.com.montreal.ai.llmontreal.repository.DocumentRepository;
import br.com.montreal.ai.llmontreal.service.extraction.ContentExtractor;
import br.com.montreal.ai.llmontreal.service.ollama.OllamaProducerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DocumentExtractionService Unit Tests")
class DocumentExtractionServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private OllamaProducerService ollamaProducerService;

    @Mock
    private ContentExtractor contentExtractor;

    private DocumentExtractionService documentExtractionService;

    @Captor
    private ArgumentCaptor<DocumentExtractionCompletedEvent> eventCaptor;

    private Document document;
    private final String extractedContent = "Este é o conteúdo extraído do documento";

    @BeforeEach
    void setUp() {
        document = Document.builder()
                .id(1L)
                .fileName("test-document.pdf")
                .fileType("application/pdf")
                .fileData("dummy file data".getBytes())
                .status(DocumentStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        documentExtractionService = new DocumentExtractionService(
                List.of(contentExtractor),
                documentRepository,
                eventPublisher,
                ollamaProducerService
        );
    }

    @Test
    @DisplayName("Should extract content successfully")
    void shouldExtractContentSuccessfully() throws Exception {
        when(documentRepository.findById(1L)).thenReturn(Optional.of(document));
        when(documentRepository.save(any(Document.class))).thenReturn(document);
        when(contentExtractor.supportsThisContentType("application/pdf")).thenReturn(true);
        when(contentExtractor.extractContent(any(InputStream.class), eq("application/pdf")))
                .thenReturn(extractedContent);

        documentExtractionService.extractContentAsync(1L, "test-correlation-id");

        verify(eventPublisher).publishEvent(eventCaptor.capture());
        DocumentExtractionCompletedEvent event = eventCaptor.getValue();

        assertThat(event.isSuccess()).isTrue();
        assertThat(event.getDocumentId()).isEqualTo(1L);
        assertThat(event.getExtractedContent()).isEqualTo(extractedContent);
        assertThat(event.getErrorMessage()).isNull();
    }

    @Test
    @DisplayName("Should handle document not found")
    void shouldHandleDocumentNotFound() {
        when(documentRepository.findById(999L)).thenReturn(Optional.empty());

        documentExtractionService.extractContentAsync(999L, "test-correlation-id");

        verify(eventPublisher).publishEvent(eventCaptor.capture());
        DocumentExtractionCompletedEvent event = eventCaptor.getValue();

        assertThat(event.isSuccess()).isFalse();
        assertThat(event.getDocumentId()).isEqualTo(999L);
        assertThat(event.getExtractedContent()).isNull();
        assertThat(event.getErrorMessage()).contains("Unexpected error");
    }

    @Test
    @DisplayName("Should handle extraction exception")
    void shouldHandleExtractionException() throws Exception {
        when(documentRepository.findById(1L)).thenReturn(Optional.of(document));
        when(documentRepository.save(any(Document.class))).thenReturn(document);
        when(contentExtractor.supportsThisContentType("application/pdf")).thenReturn(true);
        when(contentExtractor.extractContent(any(InputStream.class), eq("application/pdf")))
                .thenThrow(new ExtractionException("Failed to extract content"));

        documentExtractionService.extractContentAsync(1L, "test-correlation-id");

        verify(eventPublisher).publishEvent(eventCaptor.capture());
        DocumentExtractionCompletedEvent event = eventCaptor.getValue();

        assertThat(event.isSuccess()).isFalse();
        assertThat(event.getDocumentId()).isEqualTo(1L);
        assertThat(event.getExtractedContent()).isNull();
        assertThat(event.getErrorMessage()).isEqualTo("Failed to extract content");
    }

    @Test
    @DisplayName("Should handle unsupported content type")
    void shouldHandleUnsupportedContentType() {
        Document unsupportedDoc = Document.builder()
                .id(2L)
                .fileName("test.xyz")
                .fileType("application/xyz")
                .fileData("dummy data".getBytes())
                .status(DocumentStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        when(documentRepository.findById(2L)).thenReturn(Optional.of(unsupportedDoc));
        when(documentRepository.save(any(Document.class))).thenReturn(unsupportedDoc);
        when(contentExtractor.supportsThisContentType("application/xyz")).thenReturn(false);

        documentExtractionService.extractContentAsync(2L, "test-correlation-id");

        verify(eventPublisher).publishEvent(eventCaptor.capture());
        DocumentExtractionCompletedEvent event = eventCaptor.getValue();

        assertThat(event.isSuccess()).isFalse();
        assertThat(event.getDocumentId()).isEqualTo(2L);
        assertThat(event.getExtractedContent()).isNull();
        assertThat(event.getErrorMessage()).contains("No extractor found");
    }

    @Test
    @DisplayName("Should update document status to PROCESSING before extraction")
    void shouldUpdateDocumentStatusToProcessing() throws Exception {
        ArgumentCaptor<Document> documentCaptor = ArgumentCaptor.forClass(Document.class);

        when(documentRepository.findById(1L)).thenReturn(Optional.of(document));
        when(documentRepository.save(any(Document.class))).thenReturn(document);
        when(contentExtractor.supportsThisContentType("application/pdf")).thenReturn(true);
        when(contentExtractor.extractContent(any(InputStream.class), eq("application/pdf")))
                .thenReturn(extractedContent);

        documentExtractionService.extractContentAsync(1L, "test-correlation-id");

        verify(documentRepository).save(documentCaptor.capture());

        Document savedDocument = documentCaptor.getValue();
        assertThat(savedDocument.getStatus()).isEqualTo(DocumentStatus.PROCESSING);
        assertThat(savedDocument.getUpdatedAt()).isNotNull();
    }
}

