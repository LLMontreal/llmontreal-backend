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
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ChatService {

    @Autowired
    private final ChatSessionRepository chatSessionRepository;

    @Autowired
    private final DocumentRepository documentRepository;

    @Autowired
    private final WebClient webClient;

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);


    public Mono<OllamaResponseDTO> processMessage(OllamaRequestDTO requestDTO, Long chatSessionId, Long documentId) {
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new EntityNotFoundException("Document not found by id: " + documentId));

        ChatSession currentSession = getOrCreateSession(requestDTO.model(), doc, chatSessionId);

        log.info("Calling {} for {} session...", requestDTO.model(), currentSession.getId());

        return webClient.post()
                .uri("/api/generate")
                .body(Mono.just(requestDTO), OllamaRequestDTO.class)
                .retrieve()
                .bodyToMono(OllamaResponseDTO.class)
                .timeout(Duration.ofMinutes(2))
                .doOnSuccess(res -> {
                    log.info("Successfully received response from {}", requestDTO.model());
                    addMessageToContext(currentSession.getId(), requestDTO.prompt(), Author.USER);
                    addMessageToContext(currentSession.getId(), res.response(), Author.MODEL);
                })
                .onErrorMap(WebClientResponseException.class, ex -> {
                    log.error("API call failed to Ollama model: {}. Status: {}, Response: {}",
                            requestDTO.model(), ex.getStatusCode(), ex.getResponseBodyAsString());

                    return new OllamaException("Error communicating with Ollama: " + ex.getResponseBodyAsString(), ex);
                });
    }

    private ChatSession getOrCreateSession(String model, Document doc, Long chatSessionId) {
        if (chatSessionId == null) {
            return createChatSession(model, doc);
        }

        return chatSessionRepository.findById(chatSessionId)
                .orElseGet(() -> {
                    log.warn("ChatSession with id {} not found. Creating new session.", chatSessionId);
                    return createChatSession(model, doc);
                });
    }

    private ChatSession createChatSession(String model, Document doc) {
        log.info("Creating chat session with {}...", model);

        ChatSession cs = ChatSession.builder()
                .createdAt(LocalDateTime.now())
                .isActive(true)
                .document(doc)
                .build();
        return chatSessionRepository.save(cs);
    }

    @Transactional
    private void addMessageToContext(Long chatSessionId, String content, Author author) {
        ChatSession cs = chatSessionRepository.findById(chatSessionId)
                .orElseThrow(() -> new EntityNotFoundException("Chat Session not found by id: " + chatSessionId));

        ChatMessage chatMessage = ChatMessage.builder()
                .author(author)
                .createdAt(LocalDateTime.now())
                .message(content)
                .build();

        cs.addMessage(chatMessage);
        chatSessionRepository.save(cs);
    }

}

