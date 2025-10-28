package br.com.montreal.ai.llmontreal.service;

import br.com.montreal.ai.llmontreal.entity.Document;
import br.com.montreal.ai.llmontreal.entity.enums.DocumentStatus;
import br.com.montreal.ai.llmontreal.repository.DocumentRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.junit.jupiter.MockitoExtension;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DocumentService Unit Tests")
public class DocumentServiceTests {

    @Mock
    private DocumentRepository documentRepository;

    @InjectMocks
    private DocumentService documentService;

    private Pageable pageable;
    private List<Document> documentsList;

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
}
