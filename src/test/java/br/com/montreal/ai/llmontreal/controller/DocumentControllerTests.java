package br.com.montreal.ai.llmontreal.controller;

import br.com.montreal.ai.llmontreal.dto.DocumentUploadResponse;
import br.com.montreal.ai.llmontreal.entity.Document;
import br.com.montreal.ai.llmontreal.entity.enums.DocumentStatus;
import br.com.montreal.ai.llmontreal.exception.FileValidationException;
import br.com.montreal.ai.llmontreal.exception.GlobalExceptionHandler;
import br.com.montreal.ai.llmontreal.service.DocumentService;
import br.com.montreal.ai.llmontreal.service.ollama.OllamaLogApiCallService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DocumentController Unit Tests")
class DocumentControllerTests {

        private static final String BASE_URL = "/documents";

        private MockMvc mockMvc;

        private ObjectMapper objectMapper;

        @Mock
        private DocumentService documentService;

        @Mock
        private OllamaLogApiCallService ollamaLogApiCallService;

        @InjectMocks
        private DocumentController documentController;

        private Document document1;
        private Document document2;
        private DocumentUploadResponse uploadResponseSingle;

        @BeforeEach
        void setUp() {
                objectMapper = new ObjectMapper();
                mockMvc = MockMvcBuilders.standaloneSetup(documentController)
                                .setControllerAdvice(new GlobalExceptionHandler())
                                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                                .build();

                document1 = Document.builder()
                                .id(1L)
                                .fileName("doc1.pdf")
                                .fileType("application/pdf")
                                .status(DocumentStatus.PENDING)
                                .createdAt(LocalDateTime.now())
                                .updatedAt(LocalDateTime.now())
                                .fileData("dummy".getBytes())
                                .build();

                document2 = Document.builder()
                                .id(2L)
                                .fileName("doc2.pdf")
                                .fileType("application/pdf")
                                .status(DocumentStatus.COMPLETED)
                                .createdAt(LocalDateTime.now())
                                .updatedAt(LocalDateTime.now())
                                .fileData("dummy2".getBytes())
                                .build();

                uploadResponseSingle = new DocumentUploadResponse(document1);
        }

        @Nested
        @DisplayName("GET /documents")
        class GetDocumentsTests {

                @Test
                @DisplayName("Deve retornar página de documentos sem filtro de status")
                void shouldGetAllDocumentsWithoutStatus() throws Exception {
                        Pageable pageable = PageRequest.of(0, 20);
                        List<Document> docs = List.of(document1, document2);
                        Page<Document> page = new PageImpl<>(docs, pageable, docs.size());

                        given(documentService.getAllDocuments(any(Pageable.class), any()))
                                        .willReturn(page);

                        mockMvc.perform(get(BASE_URL)
                                        .param("page", "0")
                                        .param("size", "20")
                                        .accept(MediaType.APPLICATION_JSON))
                                        .andExpect(status().isOk())
                                        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                                        .andExpect(jsonPath("$.content", hasSize(2)))
                                        .andExpect(jsonPath("$.content[0].id", is(document1.getId().intValue())))
                                        .andExpect(jsonPath("$.content[0].fileName", is(document1.getFileName())))
                                        .andExpect(jsonPath("$.content[0].status", is(document1.getStatus().name())))
                                        .andExpect(jsonPath("$.content[1].id", is(document2.getId().intValue())))
                                        .andExpect(jsonPath("$.content[1].fileName", is(document2.getFileName())))
                                        .andExpect(jsonPath("$.content[1].status", is(document2.getStatus().name())));
                }

                @Test
                @DisplayName("Deve retornar página de documentos filtrada por status")
                void shouldGetAllDocumentsFilteredByStatus() throws Exception {
                        Pageable pageable = PageRequest.of(0, 20);
                        List<Document> docs = List.of(document2);
                        Page<Document> page = new PageImpl<>(docs, pageable, docs.size());

                        given(documentService.getAllDocuments(any(Pageable.class), any(DocumentStatus.class)))
                                        .willReturn(page);

                        mockMvc.perform(get(BASE_URL)
                                        .param("status", "COMPLETED")
                                        .param("page", "0")
                                        .param("size", "20")
                                        .accept(MediaType.APPLICATION_JSON))
                                        .andExpect(status().isOk())
                                        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                                        .andExpect(jsonPath("$.content", hasSize(1)))
                                        .andExpect(jsonPath("$.content[0].id", is(document2.getId().intValue())))
                                        .andExpect(jsonPath("$.content[0].status", is("COMPLETED")));
                }

                @Test
                @DisplayName("Deve retornar 400 quando status é inválido")
                void shouldReturnBadRequestForInvalidStatus() throws Exception {
                        mockMvc.perform(get(BASE_URL)
                                        .param("status", "INVALID_STATUS")
                                        .accept(MediaType.APPLICATION_JSON))
                                        .andExpect(status().isBadRequest())
                                        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                                        .andExpect(jsonPath("$.error", is("Bad Request")));
                }
        }

        @Nested
        @DisplayName("POST /documents")
        class UploadDocumentTests {

                @Test
                @DisplayName("Deve fazer upload de arquivo com sucesso e retornar 201")
                void shouldUploadFileSuccessfully() throws Exception {
                        MockMultipartFile file = new MockMultipartFile(
                                        "file",
                                        "doc1.pdf",
                                        "application/pdf",
                                        "dummy-content".getBytes());

                        given(documentService.uploadFile(any(), any()))
                                        .willReturn(uploadResponseSingle);

                        mockMvc.perform(multipart(BASE_URL)
                                        .file(file)
                                        .accept(MediaType.APPLICATION_JSON))
                                        .andExpect(status().isCreated())
                                        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                                        .andExpect(jsonPath("$.id", is(document1.getId().intValue())))
                                        .andExpect(jsonPath("$.fileName", is(document1.getFileName())))
                                        .andExpect(jsonPath("$.fileType", is(document1.getFileType())))
                                        .andExpect(jsonPath("$.status", is(document1.getStatus().name())))
                                        .andExpect(jsonPath("$.message").exists());
                }

                @Test
                @DisplayName("Deve retornar 400 quando o arquivo está vazio")
                void shouldReturnBadRequestWhenFileIsEmpty() throws Exception {
                        MockMultipartFile emptyFile = new MockMultipartFile(
                                        "file",
                                        "empty.pdf",
                                        "application/pdf",
                                        new byte[0]);

                        doThrow(new FileValidationException("O arquivo não pode ser nulo ou vazio"))
                                        .when(documentService).uploadFile(any(), any());

                        mockMvc.perform(multipart(BASE_URL)
                                        .file(emptyFile)
                                        .accept(MediaType.APPLICATION_JSON))
                                        .andExpect(status().isBadRequest())
                                        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                                        .andExpect(jsonPath("$.error", is("File Validation Error")));
                }

                @Test
                @DisplayName("Deve retornar 400 quando a validação de tipo de arquivo falhar")
                void shouldReturnBadRequestWhenFileValidationFails() throws Exception {
                        MockMultipartFile invalidFile = new MockMultipartFile(
                                        "file",
                                        "invalid.exe",
                                        "application/octet-stream",
                                        "dummy".getBytes());

                        doThrow(new FileValidationException("Tipo de arquivo não suportado"))
                                        .when(documentService).uploadFile(any(), any());

                        mockMvc.perform(multipart(BASE_URL)
                                        .file(invalidFile)
                                        .accept(MediaType.APPLICATION_JSON))
                                        .andExpect(status().isBadRequest())
                                        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                                        .andExpect(jsonPath("$.error", is("File Validation Error")));
                }
        }

        @Nested
        @DisplayName("GET /documents/{id}/content e /summary")
        class ContentAndSummaryTests {

                @Test
                @DisplayName("Deve retornar conteúdo extraído quando existir")
                void shouldReturnExtractedContent() throws Exception {
                        String content = "conteúdo extraído";

                        given(documentService.getExtractedContent(1L)).willReturn(java.util.Optional.of(content));

                        mockMvc.perform(get(BASE_URL + "/{id}/content", 1L)
                                        .accept(MediaType.TEXT_PLAIN))
                                        .andExpect(status().isOk())
                                        .andExpect(content().string(content));
                }

                @Test
                @DisplayName("Deve retornar 404 quando conteúdo extraído não existir")
                void shouldReturnNotFoundWhenExtractedContentNotFound() throws Exception {
                        given(documentService.getExtractedContent(99L)).willReturn(java.util.Optional.empty());

                        mockMvc.perform(get(BASE_URL + "/{id}/content", 99L)
                                        .accept(MediaType.TEXT_PLAIN))
                                        .andExpect(status().isNotFound());
                }

                @Test
                @DisplayName("Deve retornar summary quando existir")
                void shouldReturnSummary() throws Exception {
                        String summary = "resumo do documento";

                        given(documentService.getSummary(1L)).willReturn(java.util.Optional.of(summary));

                        mockMvc.perform(get(BASE_URL + "/{id}/summary", 1L)
                                        .accept(MediaType.TEXT_PLAIN))
                                        .andExpect(status().isOk())
                                        .andExpect(content().string(summary));
                }

                @Test
                @DisplayName("Deve retornar 404 quando summary não existir")
                void shouldReturnNotFoundWhenSummaryNotFound() throws Exception {
                        given(documentService.getSummary(99L)).willReturn(java.util.Optional.empty());

                        mockMvc.perform(get(BASE_URL + "/{id}/summary", 99L)
                                        .accept(MediaType.TEXT_PLAIN))
                                        .andExpect(status().isNotFound());
                }
        }
}