package br.com.montreal.ai.llmontreal.controller;

import br.com.montreal.ai.llmontreal.dto.ChatMessageRequestDTO;
import br.com.montreal.ai.llmontreal.dto.ChatMessageResponseDTO;
import br.com.montreal.ai.llmontreal.entity.ChatSession;
import br.com.montreal.ai.llmontreal.entity.Document;
import br.com.montreal.ai.llmontreal.entity.enums.Author;
import br.com.montreal.ai.llmontreal.entity.enums.DocumentStatus;
import br.com.montreal.ai.llmontreal.repository.ChatSessionRepository;
import br.com.montreal.ai.llmontreal.repository.DocumentRepository;
import br.com.montreal.ai.llmontreal.service.ChatProducerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ChatControllerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private ChatSessionRepository chatSessionRepository;

    @MockitoBean
    private ChatProducerService chatProducerService;

    private Document testDocument;
    private ChatSession testChatSession;

    @BeforeEach
    void setUp() {
        documentRepository.deleteAll();
        chatSessionRepository.deleteAll();

        testChatSession = ChatSession.builder()
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .build();
        testChatSession = chatSessionRepository.saveAndFlush(testChatSession);

        testDocument = Document.builder()
                .fileName("test-document.pdf")
                .status(DocumentStatus.COMPLETED)
                .createdAt(LocalDateTime.now())
                .fileType("application/pdf")
                .fileData("test content".getBytes())
                .extractedContent("This is the extracted content for testing chat")
                .chatSession(testChatSession)
                .build();
        testDocument = documentRepository.saveAndFlush(testDocument);

        testChatSession.setDocument(testDocument);
        testChatSession = chatSessionRepository.saveAndFlush(testChatSession);
    }

    @Test
    void shouldSendMessageSuccessfully() throws Exception {
        ChatMessageRequestDTO requestDTO = ChatMessageRequestDTO.builder()
                .model("llama2")
                .prompt("What is this document about?")
                .stream(false)
                .build();

        ChatMessageResponseDTO expectedResponse = ChatMessageResponseDTO.builder()
                .documentId(testDocument.getId())
                .chatSessionId(testChatSession.getId())
                .author(Author.MODEL)
                .createdAt(LocalDateTime.now())
                .response("This document discusses testing strategies for Spring Boot applications.")
                .build();

        when(chatProducerService.processMessage(any(ChatMessageRequestDTO.class), eq(testDocument.getId())))
                .thenReturn(CompletableFuture.completedFuture(expectedResponse));

        mockMvc.perform(post("/chat/{documentId}", testDocument.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentId", is(testDocument.getId().intValue())))
                .andExpect(jsonPath("$.chatSessionId", is(testChatSession.getId().intValue())))
                .andExpect(jsonPath("$.author", is(Author.MODEL.name())))
                .andExpect(jsonPath("$.response", is("This document discusses testing strategies for Spring Boot applications.")))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    void shouldReturnBadRequestWhenPromptIsBlank() throws Exception {
        ChatMessageRequestDTO requestDTO = ChatMessageRequestDTO.builder()
                .model("llama2")
                .prompt("")
                .stream(false)
                .build();

        mockMvc.perform(post("/chat/{documentId}", testDocument.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDTO)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnBadRequestWhenPromptIsNull() throws Exception {
        ChatMessageRequestDTO requestDTO = ChatMessageRequestDTO.builder()
                .model("llama2")
                .prompt(null)
                .stream(false)
                .build();

        mockMvc.perform(post("/chat/{documentId}", testDocument.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDTO)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldSendMessageWithDifferentModel() throws Exception {
        ChatMessageRequestDTO requestDTO = ChatMessageRequestDTO.builder()
                .model("mistral")
                .prompt("Summarize this document")
                .stream(false)
                .build();

        ChatMessageResponseDTO expectedResponse = ChatMessageResponseDTO.builder()
                .documentId(testDocument.getId())
                .chatSessionId(testChatSession.getId())
                .author(Author.MODEL)
                .createdAt(LocalDateTime.now())
                .response("The document provides an overview of integration testing.")
                .build();

        when(chatProducerService.processMessage(any(ChatMessageRequestDTO.class), eq(testDocument.getId())))
                .thenReturn(CompletableFuture.completedFuture(expectedResponse));

        mockMvc.perform(post("/chat/{documentId}", testDocument.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentId", is(testDocument.getId().intValue())))
                .andExpect(jsonPath("$.author", is(Author.MODEL.name())))
                .andExpect(jsonPath("$.response", is("The document provides an overview of integration testing.")));
    }

    @Test
    void shouldSendMessageWithLongPrompt() throws Exception {
        String longPrompt = "Can you provide a detailed analysis of the document including " +
                "its main points, key takeaways, and recommendations? " +
                "Please be as thorough as possible in your response.";

        ChatMessageRequestDTO requestDTO = ChatMessageRequestDTO.builder()
                .model("llama2")
                .prompt(longPrompt)
                .stream(false)
                .build();

        ChatMessageResponseDTO expectedResponse = ChatMessageResponseDTO.builder()
                .documentId(testDocument.getId())
                .chatSessionId(testChatSession.getId())
                .author(Author.MODEL)
                .createdAt(LocalDateTime.now())
                .response("Here is a detailed analysis of the document...")
                .build();

        when(chatProducerService.processMessage(any(ChatMessageRequestDTO.class), eq(testDocument.getId())))
                .thenReturn(CompletableFuture.completedFuture(expectedResponse));

        mockMvc.perform(post("/chat/{documentId}", testDocument.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response", is("Here is a detailed analysis of the document...")));
    }

    @Test
    void shouldHandleMultipleMessagesToSameDocument() throws Exception {
        ChatMessageRequestDTO firstRequest = ChatMessageRequestDTO.builder()
                .model("llama2")
                .prompt("What is this document about?")
                .stream(false)
                .build();

        ChatMessageResponseDTO firstResponse = ChatMessageResponseDTO.builder()
                .documentId(testDocument.getId())
                .chatSessionId(testChatSession.getId())
                .author(Author.MODEL)
                .createdAt(LocalDateTime.now())
                .response("First response")
                .build();

        when(chatProducerService.processMessage(any(ChatMessageRequestDTO.class), eq(testDocument.getId())))
                .thenReturn(CompletableFuture.completedFuture(firstResponse));

        mockMvc.perform(post("/chat/{documentId}", testDocument.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(firstRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response", is("First response")));

        ChatMessageRequestDTO secondRequest = ChatMessageRequestDTO.builder()
                .model("llama2")
                .prompt("Can you elaborate on that?")
                .stream(false)
                .build();

        ChatMessageResponseDTO secondResponse = ChatMessageResponseDTO.builder()
                .documentId(testDocument.getId())
                .chatSessionId(testChatSession.getId())
                .author(Author.MODEL)
                .createdAt(LocalDateTime.now())
                .response("Second response with more details")
                .build();

        when(chatProducerService.processMessage(any(ChatMessageRequestDTO.class), eq(testDocument.getId())))
                .thenReturn(CompletableFuture.completedFuture(secondResponse));

        mockMvc.perform(post("/chat/{documentId}", testDocument.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(secondRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response", is("Second response with more details")));
    }
}
