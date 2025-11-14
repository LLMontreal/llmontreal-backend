package br.com.montreal.ai.llmontreal.controller;

import br.com.montreal.ai.llmontreal.config.TestOllamaConfig;
import br.com.montreal.ai.llmontreal.entity.Document;
import br.com.montreal.ai.llmontreal.entity.enums.DocumentStatus;
import br.com.montreal.ai.llmontreal.repository.DocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.mock.web.MockMultipartFile;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestOllamaConfig.class)
class DocumentControllerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DocumentRepository documentRepository;

    private static final int TOTAL_ELEMENTS = 8;
    private static final int PAGE_SIZE = 4;
    private static final int DOCUMENTS_FOR_EACH_STATUS = 2;

    @BeforeEach
    void setUp() {
        documentRepository.deleteAll();

        LocalDateTime now = LocalDateTime.now();

        List<Document> documents = List.of(
                createTestDocument("doc1.pdf", DocumentStatus.PENDING, now.minusSeconds(7)),
                createTestDocument("doc2.pdf", DocumentStatus.COMPLETED, now.minusSeconds(6)),
                createTestDocument("doc3.pdf", DocumentStatus.PROCESSING, now.minusSeconds(5)),
                createTestDocument("doc4.pdf", DocumentStatus.FAILED, now.minusSeconds(4)),
                createTestDocument("doc5.pdf", DocumentStatus.PENDING, now.minusSeconds(3)),
                createTestDocument("doc6.pdf", DocumentStatus.COMPLETED, now.minusSeconds(2)),
                createTestDocument("doc7.pdf", DocumentStatus.PROCESSING, now.minusSeconds(1)),
                createTestDocument("doc8.pdf", DocumentStatus.FAILED, now)
        );

        documentRepository.saveAllAndFlush(documents);
    }

    private Document createTestDocument(String fileName, DocumentStatus status, LocalDateTime createdAt) {
        return Document.builder()
                .fileName(fileName)
                .status(status)
                .createdAt(createdAt)
                .fileType("application/pdf")
                .fileData("test content".getBytes())
                .build();
    }

    @Test
    void shouldGetAllDocumentsWithPagination() throws Exception {
        mockMvc.perform(get("/documents")
                        .param("page", "0")
                        .param("size", String.valueOf(PAGE_SIZE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(PAGE_SIZE)))
                .andExpect(jsonPath("$.totalElements", is(TOTAL_ELEMENTS)))
                .andExpect(jsonPath("$.number", is(0)))
                .andExpect(jsonPath("$.sort.sorted", is(false)));
    }

    @Test
    void shouldGetSecondPageOfDocuments() throws Exception {
        mockMvc.perform(get("/documents")
                        .param("page", "1")
                        .param("size", "4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(PAGE_SIZE)))
                .andExpect(jsonPath("$.totalElements", is(TOTAL_ELEMENTS)))
                .andExpect(jsonPath("$.totalPages", is(TOTAL_ELEMENTS / PAGE_SIZE)))
                .andExpect(jsonPath("$.number", is(1)))
                .andExpect(jsonPath("$.last", is(true)));
    }

    @Test
    void shouldGetAllDocumentsPaginatedAndSorted() throws Exception {
        mockMvc.perform(get("/documents")
                    .param("page", "0")
                    .param("size", String.valueOf(PAGE_SIZE))
                    .param("sort", "createdAt,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(PAGE_SIZE)))
                .andExpect(jsonPath("$.totalElements", is(TOTAL_ELEMENTS)))
                .andExpect(jsonPath("$.number", is(0)))
                .andExpect(jsonPath("$.sort.sorted", is(true)))
                .andExpect(jsonPath("$.content[0].fileName", is("doc8.pdf")))
                .andExpect(jsonPath("$.content[1].fileName", is("doc7.pdf")))
                .andExpect(jsonPath("$.content[2].fileName", is("doc6.pdf")))
                .andExpect(jsonPath("$.content[3].fileName", is("doc5.pdf")));
    }

    @ParameterizedTest
    @EnumSource(DocumentStatus.class)
    void shouldGetAllDocumentsByStatus(DocumentStatus status) throws Exception {
        mockMvc.perform(get("/documents")
                        .param("status", status.name())
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(DOCUMENTS_FOR_EACH_STATUS)))
                .andExpect(jsonPath("$.totalElements", is(DOCUMENTS_FOR_EACH_STATUS)))
                .andExpect(jsonPath("$.content[*].status", everyItem(is(status.name()))));
    }

    @Test
    void shouldReturnEmptyPageWhenNoDocumentsExist() throws Exception {
        documentRepository.deleteAll();

        mockMvc.perform(get("/documents")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements", is(0)))
                .andExpect(jsonPath("$.totalPages", is(0)))
                .andExpect(jsonPath("$.empty", is(true)));
    }

    @Test
    void shouldReturnBadRequestForInvalidStatus() throws Exception {
        mockMvc.perform(get("/documents")
                        .param("status", "INVALID_STATUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Bad Request")))
                .andExpect(jsonPath("$.errorMessage",
                        containsString("Invalid value 'INVALID_STATUS' for 'status' param")))
                .andExpect(jsonPath("$.path", is("/documents")));
    }

    @Test
    void shouldUploadFileSuccessfully() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test-document.pdf",
                "application/pdf",
                "PDF file content".getBytes()
        );

        mockMvc.perform(multipart("/documents")
                        .file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.fileName", is("test-document.pdf")))
                .andExpect(jsonPath("$.status", is(DocumentStatus.PENDING.name())))
                .andExpect(jsonPath("$.uploadedAt", notNullValue()));
    }

    @Test
    void shouldReturnBadRequestWhenFileIsMissing() throws Exception {
        mockMvc.perform(multipart("/documents"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldUploadMultipleFilesSuccessfully() throws Exception {
        MockMultipartFile file1 = new MockMultipartFile(
                "file",
                "document1.pdf",
                "application/pdf",
                "Content 1".getBytes()
        );

        MockMultipartFile file2 = new MockMultipartFile(
                "file",
                "document2.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "Content 2".getBytes()
        );

        mockMvc.perform(multipart("/documents").file(file1))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileName", is("document1.pdf")));

        mockMvc.perform(multipart("/documents").file(file2))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileName", is("document2.docx")));
    }

    @Test
    void shouldGetExtractedContentSuccessfully() throws Exception {
        Document document = Document.builder()
                .fileName("completed-doc.pdf")
                .status(DocumentStatus.COMPLETED)
                .createdAt(LocalDateTime.now())
                .fileType("application/pdf")
                .fileData("original content".getBytes())
                .extractedContent("This is the extracted text content")
                .build();

        Document savedDocument = documentRepository.saveAndFlush(document);

        mockMvc.perform(get("/documents/{id}/content", savedDocument.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string("This is the extracted text content"));
    }

    @Test
    void shouldReturnNotFoundWhenDocumentDoesNotExist() throws Exception {
        Long nonExistentId = 99999L;

        mockMvc.perform(get("/documents/{id}/content", nonExistentId))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnNotFoundWhenExtractedContentIsNull() throws Exception {
        Document document = Document.builder()
                .fileName("pending-doc.pdf")
                .status(DocumentStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .fileType("application/pdf")
                .fileData("original content".getBytes())
                .extractedContent(null)
                .build();

        Document savedDocument = documentRepository.saveAndFlush(document);

        mockMvc.perform(get("/documents/{id}/content", savedDocument.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldGetExtractedContentForCompletedDocument() throws Exception {
        Document document = Document.builder()
                .fileName("processed-doc.pdf")
                .status(DocumentStatus.COMPLETED)
                .createdAt(LocalDateTime.now())
                .fileType("application/pdf")
                .fileData("original content".getBytes())
                .extractedContent("Complete extracted content with multiple lines\nLine 2\nLine 3")
                .build();

        Document savedDocument = documentRepository.saveAndFlush(document);

        mockMvc.perform(get("/documents/{id}/content", savedDocument.getId()))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/plain;charset=UTF-8"))
                .andExpect(content().string(containsString("Complete extracted content")))
                .andExpect(content().string(containsString("Line 2")))
                .andExpect(content().string(containsString("Line 3")));
    }
}
