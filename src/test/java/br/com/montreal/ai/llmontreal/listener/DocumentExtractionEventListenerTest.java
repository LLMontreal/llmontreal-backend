package br.com.montreal.ai.llmontreal.listener;

import br.com.montreal.ai.llmontreal.entity.Document;
import br.com.montreal.ai.llmontreal.entity.enums.DocumentStatus;
import br.com.montreal.ai.llmontreal.event.DocumentExtractionCompletedEvent;
import br.com.montreal.ai.llmontreal.repository.DocumentRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DocumentExtractionEventListener Unit Tests")
class DocumentExtractionEventListenerTest {

    @Mock
    private DocumentRepository documentRepository;

    @InjectMocks
    private DocumentExtractionEventListener listener;

    @Captor
    private ArgumentCaptor<Document> documentCaptor;

    private Document document;
    private final String extractedContent = "Conteúdo extraído do documento";

    @BeforeEach
    void setUp() {
        document = Document.builder()
                .id(1L)
                .fileName("test-document.pdf")
                .fileType("application/pdf")
                .fileData("dummy data".getBytes())
                .status(DocumentStatus.PROCESSING)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("Should handle successful extraction event")
    void shouldHandleSuccessfulExtractionEvent() {
        DocumentExtractionCompletedEvent event = DocumentExtractionCompletedEvent.success(
                this, 1L, extractedContent
        );

        when(documentRepository.findById(1L)).thenReturn(Optional.of(document));
        when(documentRepository.save(any(Document.class))).thenReturn(document);

        listener.handleExtractionCompleted(event);

        verify(documentRepository).save(documentCaptor.capture());
        Document savedDocument = documentCaptor.getValue();

        assertThat(savedDocument.getExtractedContent()).isEqualTo(extractedContent);
        assertThat(savedDocument.getStatus()).isEqualTo(DocumentStatus.COMPLETED);
        assertThat(savedDocument.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should handle failed extraction event")
    void shouldHandleFailedExtractionEvent() {
        DocumentExtractionCompletedEvent event = DocumentExtractionCompletedEvent.failure(
                this, 1L, "Erro na extração"
        );

        when(documentRepository.findById(1L)).thenReturn(Optional.of(document));
        when(documentRepository.save(any(Document.class))).thenReturn(document);

        listener.handleExtractionCompleted(event);

        verify(documentRepository).save(documentCaptor.capture());
        Document savedDocument = documentCaptor.getValue();

        assertThat(savedDocument.getExtractedContent()).isNull();
        assertThat(savedDocument.getStatus()).isEqualTo(DocumentStatus.FAILED);
        assertThat(savedDocument.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should throw EntityNotFoundException when document is not found")
    void shouldThrowExceptionWhenDocumentNotFound() {
        DocumentExtractionCompletedEvent event = DocumentExtractionCompletedEvent.success(
                this, 999L, extractedContent
        );

        when(documentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> listener.handleExtractionCompleted(event))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Document not found with id: 999");

        verify(documentRepository, never()).save(any(Document.class));
    }

    @Test
    @DisplayName("Should update timestamp on extraction completion")
    void shouldUpdateTimestampOnExtractionCompletion() {
        LocalDateTime oldTimestamp = document.getUpdatedAt();
        DocumentExtractionCompletedEvent event = DocumentExtractionCompletedEvent.success(
                this, 1L, extractedContent
        );

        when(documentRepository.findById(1L)).thenReturn(Optional.of(document));
        when(documentRepository.save(any(Document.class))).thenReturn(document);

        listener.handleExtractionCompleted(event);

        verify(documentRepository).save(documentCaptor.capture());
        Document savedDocument = documentCaptor.getValue();

        assertThat(savedDocument.getUpdatedAt()).isNotNull();
        assertThat(savedDocument.getUpdatedAt()).isAfterOrEqualTo(oldTimestamp);
    }

    @Test
    @DisplayName("Should not persist error message in document")
    void shouldNotPersistErrorMessageInDocument() {
        String errorMessage = "Erro crítico na extração";
        DocumentExtractionCompletedEvent event = DocumentExtractionCompletedEvent.failure(
                this, 1L, errorMessage
        );

        when(documentRepository.findById(1L)).thenReturn(Optional.of(document));
        when(documentRepository.save(any(Document.class))).thenReturn(document);

        listener.handleExtractionCompleted(event);

        verify(documentRepository).save(documentCaptor.capture());
        Document savedDocument = documentCaptor.getValue();

        assertThat(savedDocument.getExtractedContent()).isNull();
        assertThat(savedDocument.getStatus()).isEqualTo(DocumentStatus.FAILED);
    }
}