package br.com.montreal.ai.llmontreal.controller;

import br.com.montreal.ai.llmontreal.entity.Document;
import br.com.montreal.ai.llmontreal.entity.enums.DocumentStatus;
import br.com.montreal.ai.llmontreal.exception.GlobalExceptionHandler;
import br.com.montreal.ai.llmontreal.service.DocumentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@WebMvcTest(DocumentController.class)
@Import(GlobalExceptionHandler.class)
public class DocumentControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DocumentService documentService;

    private Pageable pageable;
    private List<Document> documents;
    private Page<Document> documentPage;

    @BeforeEach
    void setUp() {
        documents = List.of(
                Document.builder().id(1L).fileName("doc1.pdf").status(DocumentStatus.COMPLETED).build(),
                Document.builder().id(2L).fileName("doc2.pdf").status(DocumentStatus.PROCESSING).build(),
                Document.builder().id(3L).fileName("doc3.pdf").status(DocumentStatus.FAILED).build(),
                Document.builder().id(4L).fileName("doc4.pdf").status(DocumentStatus.COMPLETED).build(),
                Document.builder().id(5L).fileName("doc5.pdf").status(DocumentStatus.PROCESSING).build(),
                Document.builder().id(6L).fileName("doc6.pdf").status(DocumentStatus.FAILED).build()
        );

        pageable = PageRequest.of(0, 3);
        documentPage = new PageImpl<>(documents.subList(0, 3), pageable, documents.size());
    }

    @Test
    void shouldGetAllDocuments() throws Exception {
        when(documentService.getAllDocuments(any(Pageable.class), isNull()))
                .thenReturn(documentPage);

        mockMvc.perform(MockMvcRequestBuilders.get("/documents")
                        .param("page", "0")
                        .param("size", "3")
                )
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.content", hasSize(3)))
                .andExpect(MockMvcResultMatchers.jsonPath("$.totalElements", is(6)))
                .andExpect(MockMvcResultMatchers.jsonPath("$.totalPages", is(2)))
                .andExpect(MockMvcResultMatchers.jsonPath("$.number", is(0)));
        verify(documentService).getAllDocuments(any(Pageable.class), isNull());
    }

    @ParameterizedTest
    @EnumSource(DocumentStatus.class)
    void shouldGetAllDocumentsByStatus(DocumentStatus status) throws Exception {
        List<Document> filteredDocs = documents.stream()
                .filter(doc -> doc.getStatus() == status)
                .toList();

        documentPage = new PageImpl<>(filteredDocs, pageable, filteredDocs.size());

        when(documentService.getAllDocuments(any(Pageable.class), eq(status)))
                .thenReturn(documentPage);

        mockMvc.perform(MockMvcRequestBuilders.get("/documents")
                        .param("status", status.name())
                        .param("page", "0")
                        .param("size", "3"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.content", hasSize(filteredDocs.size())))
                .andExpect(MockMvcResultMatchers.jsonPath("$.totalElements", is(filteredDocs.size())))
                .andExpect(MockMvcResultMatchers.jsonPath("$.number", is(0)))
                .andExpect(MockMvcResultMatchers.jsonPath("$.content[*].status",
                    everyItem(is(status.name()))));
        verify(documentService).getAllDocuments(any(Pageable.class), eq(status));
    }

    @Test
    void shouldThrowMethodArgumentTypeMismatchException() throws Exception {
        String invalidValue = "INVALID";
        String paramName = "status";

        mockMvc.perform(MockMvcRequestBuilders.get("/documents")
                        .param(paramName, invalidValue))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.status", is(400)))
                .andExpect(MockMvcResultMatchers.jsonPath("$.error", is("Bad Request")))
                .andExpect(MockMvcResultMatchers.jsonPath("$.errorMessage",
                        containsString(String.format(
                                "Invalid value '%s' for '%s' param", invalidValue, paramName
                        ))))
                .andExpect(MockMvcResultMatchers.jsonPath("$.path", is("/documents")));
        verifyNoInteractions(documentService);
    }


}
