package br.com.montreal.ai.llmontreal.controller;

import br.com.montreal.ai.llmontreal.config.TestOllamaConfig;
import br.com.montreal.ai.llmontreal.dto.ChatMessageResponseDTO;
import br.com.montreal.ai.llmontreal.dto.OllamaRequestDTO;
import br.com.montreal.ai.llmontreal.entity.ChatSession;
import br.com.montreal.ai.llmontreal.entity.Document;
import br.com.montreal.ai.llmontreal.entity.enums.Author;
import br.com.montreal.ai.llmontreal.entity.enums.DocumentStatus;
import br.com.montreal.ai.llmontreal.repository.ChatSessionRepository;
import br.com.montreal.ai.llmontreal.repository.DocumentRepository;
import br.com.montreal.ai.llmontreal.service.ollama.OllamaProducerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
@Import(TestOllamaConfig.class)
@EmbeddedKafka(partitions = 1, topics = {"chat_requests", "chat_responses", "summary_requests", "summary_responses"})
class ChatControllerIntegrationTests {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private ChatSessionRepository chatSessionRepository;

    @MockitoBean
    private OllamaProducerService chatProducerService;

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

        testDocument = Document.builder()
                .fileName("test-document.pdf")
                .status(DocumentStatus.COMPLETED)
                .createdAt(LocalDateTime.now())
                .fileType("application/pdf")
                .fileData("test content".getBytes())
                .extractedContent("This is the extracted content for testing chat")
                .chatSession(testChatSession)
                .build();

        testChatSession.setDocument(testDocument);

        testDocument = documentRepository.saveAndFlush(testDocument);

        testChatSession = testDocument.getChatSession();
    }

    @Test
    void shouldSendMessageSuccessfully() {
        OllamaRequestDTO requestDTO = OllamaRequestDTO.builder()
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

        when(chatProducerService.sendChatRequest(any(OllamaRequestDTO.class), eq(testDocument.getId()), any(String.class)))
                .thenReturn(CompletableFuture.completedFuture(expectedResponse));

        webTestClient.post()
                .uri("/chat/{documentId}", testDocument.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestDTO)
                .exchange()
                .expectStatus().isOk()
                .expectBody(ChatMessageResponseDTO.class)
                .consumeWith(response -> {
                    ChatMessageResponseDTO body = response.getResponseBody();
                    assert body != null;
                    assert body.documentId().equals(testDocument.getId());
                    assert body.chatSessionId().equals(testChatSession.getId());
                    assert body.author().equals(Author.MODEL);
                    assert body.response().equals("This document discusses testing strategies for Spring Boot applications.");
                    assert body.createdAt() != null;
                });
    }

    @Test
    void shouldReturnBadRequestWhenPromptIsBlank() {
        OllamaRequestDTO requestDTO = OllamaRequestDTO.builder()
                .model("llama2")
                .prompt("")
                .stream(false)
                .build();

        webTestClient.post()
                .uri("/chat/{documentId}", testDocument.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestDTO)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void shouldReturnBadRequestWhenPromptIsNull() {
        OllamaRequestDTO requestDTO = OllamaRequestDTO.builder()
                .model("llama2")
                .prompt(null)
                .stream(false)
                .build();

        webTestClient.post()
                .uri("/chat/{documentId}", testDocument.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestDTO)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void shouldSendMessageWithDifferentModel() {
        OllamaRequestDTO requestDTO = OllamaRequestDTO.builder()
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

        when(chatProducerService.sendChatRequest(any(OllamaRequestDTO.class), eq(testDocument.getId()), any(String.class)))
                .thenReturn(CompletableFuture.completedFuture(expectedResponse));

        webTestClient.post()
                .uri("/chat/{documentId}", testDocument.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestDTO)
                .exchange()
                .expectStatus().isOk()
                .expectBody(ChatMessageResponseDTO.class)
                .consumeWith(response -> {
                    ChatMessageResponseDTO body = response.getResponseBody();
                    assert body != null;
                    assert body.documentId().equals(testDocument.getId());
                    assert body.author().equals(Author.MODEL);
                    assert body.response().equals("The document provides an overview of integration testing.");
                });
    }

    @Test
    void shouldSendMessageWithLongPrompt() {
        String longPrompt = "Can you provide a detailed analysis of the document including " +
                "its main points, key takeaways, and recommendations? " +
                "Please be as thorough as possible in your response.";

        OllamaRequestDTO requestDTO = OllamaRequestDTO.builder()
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

        when(chatProducerService.sendChatRequest(any(OllamaRequestDTO.class), eq(testDocument.getId()), any(String.class)))
                .thenReturn(CompletableFuture.completedFuture(expectedResponse));

        webTestClient.post()
                .uri("/chat/{documentId}", testDocument.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestDTO)
                .exchange()
                .expectStatus().isOk()
                .expectBody(ChatMessageResponseDTO.class)
                .consumeWith(response -> {
                    ChatMessageResponseDTO body = response.getResponseBody();
                    assert body != null;
                    assert body.response().equals("Here is a detailed analysis of the document...");
                });
    }

    @Test
    void shouldHandleMultipleMessagesToSameDocument() {
        OllamaRequestDTO firstRequest = OllamaRequestDTO.builder()
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

        when(chatProducerService.sendChatRequest(any(OllamaRequestDTO.class), eq(testDocument.getId()), any(String.class)))
                .thenReturn(CompletableFuture.completedFuture(firstResponse));

        webTestClient.post()
                .uri("/chat/{documentId}", testDocument.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(firstRequest)
                .exchange()
                .expectStatus().isOk()
                .expectBody(ChatMessageResponseDTO.class)
                .consumeWith(response -> {
                    ChatMessageResponseDTO body = response.getResponseBody();
                    assert body != null;
                    assert body.response().equals("First response");
                });

        OllamaRequestDTO secondRequest = OllamaRequestDTO.builder()
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

        when(chatProducerService.sendChatRequest(any(OllamaRequestDTO.class), eq(testDocument.getId()), any(String.class)))
                .thenReturn(CompletableFuture.completedFuture(secondResponse));

        webTestClient.post()
                .uri("/chat/{documentId}", testDocument.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(secondRequest)
                .exchange()
                .expectStatus().isOk()
                .expectBody(ChatMessageResponseDTO.class)
                .consumeWith(response -> {
                    ChatMessageResponseDTO body = response.getResponseBody();
                    assert body != null;
                    assert body.response().equals("Second response with more details");
                });
    }
}
