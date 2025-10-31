package br.com.montreal.ai.llmontreal.service;

import br.com.montreal.ai.llmontreal.dto.OllamaRequestDTO;
import br.com.montreal.ai.llmontreal.dto.OllamaResponseDTO;
import br.com.montreal.ai.llmontreal.entity.ChatMessage;
import br.com.montreal.ai.llmontreal.entity.ChatSession;
import br.com.montreal.ai.llmontreal.entity.Document;
import br.com.montreal.ai.llmontreal.entity.enums.Author;
import br.com.montreal.ai.llmontreal.exception.OllamaException;
import br.com.montreal.ai.llmontreal.repository.ChatSessionRepository;
import br.com.montreal.ai.llmontreal.repository.DocumentRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChatService Unit Tests")
public class ChatServiceTests {

    @Mock
    private ChatSessionRepository chatSessionRepository;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private WebClient webClient;

    // WebClient flow mocks
    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;
    @Mock
    private WebClient.RequestBodySpec requestBodySpec;
    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;
    @Mock
    private WebClient.ResponseSpec responseSpec;

    @InjectMocks
    private ChatService chatService;

    private OllamaRequestDTO requestDTO;
    private OllamaResponseDTO mockResponseDTO;
    private ChatSession mockSession;
    private Document mockDocument;

    private final Long DOC_ID = 1L;
    private final Long SESSION_ID = 1L;

    @BeforeEach
    void setUp() {
        requestDTO = new OllamaRequestDTO(
                "test-model",
                "wakey wakey",
                false
        );

        mockResponseDTO = new OllamaResponseDTO(
                0L,
                0L,
                Author.MODEL.name(),
                LocalDateTime.now(),
                "eggs and bakey"
        );

        mockSession = spy(new ChatSession());
        mockSession.setId(SESSION_ID);
        mockDocument = new Document();
        mockDocument.setId(DOC_ID);

        lenient().when(webClient.post()).thenReturn(requestBodyUriSpec);
        lenient().when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        lenient().when(requestBodySpec.body(any(Mono.class), eq(OllamaRequestDTO.class))).thenReturn(requestHeadersSpec);
        lenient().when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
    }

    @Test
    @DisplayName("processMessage should succeed when session and document is found")
    void processMessage_shouldSucceed_whenSessionExists() {
        when(chatSessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(mockSession));
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(mockDocument));
        when(responseSpec.bodyToMono(OllamaResponseDTO.class)).thenReturn(Mono.just(mockResponseDTO));

        Mono<OllamaResponseDTO> result = chatService.processMessage(requestDTO, SESSION_ID, DOC_ID);

        StepVerifier.create(result)
                .expectNext(mockResponseDTO)
                .verifyComplete();

        verify(chatSessionRepository, times(2)).findById(1L);
        verify(documentRepository, times(1)).findById(1L);
        verify(chatSessionRepository, times(1)).save(mockSession);
        verify(mockSession, times(1)).addMessage(any(ChatMessage.class));

    }

    @Test
    @DisplayName("processMessage should succeed on save when session is created")
    void processMessage_shouldSucceed_whenSessionIsCreated() {
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(mockDocument));

        when(chatSessionRepository.findById(SESSION_ID))
                .thenReturn(Optional.empty()) // processMessage currentSession call
                .thenReturn(Optional.of(mockSession)); // addMessageToContext call

        when(chatSessionRepository.save(any(ChatSession.class))).thenReturn(mockSession);
        when(responseSpec.bodyToMono(OllamaResponseDTO.class)).thenReturn(Mono.just(mockResponseDTO));

        Mono<OllamaResponseDTO> result = chatService.processMessage(requestDTO, SESSION_ID, DOC_ID);

        StepVerifier.create(result)
                .expectNext(mockResponseDTO)
                .verifyComplete();

        verify(documentRepository, times(1)).findById(DOC_ID);
        verify(chatSessionRepository, times(2)).findById(SESSION_ID);
        verify(chatSessionRepository, times(2)).save(any(ChatSession.class));
        verify(mockSession, times(1)).addMessage(any(ChatMessage.class));

    }

    @Test
    @DisplayName("processMessage should fail on save when session is not found")
    void processMessage_shouldFailOnSave_whenSessionIsNotFound() {
        when(chatSessionRepository.findById(1L)).thenReturn(Optional.empty());
        when(documentRepository.findById(1L)).thenReturn(Optional.of(mockDocument));
        when(responseSpec.bodyToMono(OllamaResponseDTO.class)).thenReturn(Mono.just(mockResponseDTO));


        Mono<OllamaResponseDTO> result = chatService.processMessage(requestDTO, 1L, 1L);

        StepVerifier.create(result)
                .expectError(EntityNotFoundException.class)
                .verify();

        verify(chatSessionRepository, times(2)).findById(1L);
        verify(documentRepository).findById(1L);
        verify(chatSessionRepository, times(1)).save(any(ChatSession.class));
    }

    @Test
    @DisplayName("processMessage should throw EntityNotFoundException if document not found")
    void processMessage_shouldThrowEntityNotFound_whenDocumentNotExists() {
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.empty());

        EntityNotFoundException exception = assertThrows(EntityNotFoundException.class, () -> {
            chatService.processMessage(requestDTO, SESSION_ID, DOC_ID);
        });

        assertThat(exception.getMessage()).isEqualTo("Document not found by id: " + DOC_ID);
        verify(webClient, never()).post();
    }

    @Test
    @DisplayName("processMessage should map to OllamaException on API failure")
    void processMessage_shouldThrowOllamaException_whenApiCallFails() {
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(mockDocument));
        when(chatSessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(mockSession));

        WebClientResponseException mockWebException = WebClientResponseException.create(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Server Error",
                null,
                "Erro interno da API".getBytes(),
                null
        );

        when(responseSpec.bodyToMono(OllamaResponseDTO.class)).thenReturn(Mono.error(mockWebException));

        Mono<OllamaResponseDTO> result = chatService.processMessage(requestDTO, SESSION_ID, DOC_ID);

        StepVerifier.create(result)
                .expectError(OllamaException.class)
                .verify();

        verify(documentRepository, times(1)).findById(DOC_ID);
        verify(chatSessionRepository, times(1)).findById(SESSION_ID);
        verify(chatSessionRepository, never()).save(any());
    }
}
