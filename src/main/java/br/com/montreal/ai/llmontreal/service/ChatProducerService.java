package br.com.montreal.ai.llmontreal.service;

import br.com.montreal.ai.llmontreal.config.KafkaTopicConfig;
import br.com.montreal.ai.llmontreal.dto.KafkaRequestDTO;
import br.com.montreal.ai.llmontreal.dto.ChatMessageRequestDTO;
import br.com.montreal.ai.llmontreal.dto.ChatMessageResponseDTO;
import br.com.montreal.ai.llmontreal.entity.ChatSession;
import br.com.montreal.ai.llmontreal.entity.Document;
import br.com.montreal.ai.llmontreal.entity.enums.Author;
import br.com.montreal.ai.llmontreal.repository.DocumentRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class ChatProducerService {

    private final ChatService chatService;
    private final DocumentRepository documentRepository;

    private final KafkaTemplate<String, KafkaRequestDTO> kafkaTemplate;
    private final PendingRequestsService pendingRequestsService;

    private static final Logger log = LoggerFactory.getLogger(ChatProducerService.class);

    public CompletableFuture<ChatMessageResponseDTO> processMessage(ChatMessageRequestDTO requestDTO, Long documentId) {
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new EntityNotFoundException("Document not found by id: " + documentId));

        ChatSession currentSession = chatService.getOrCreateSession(requestDTO.model(), doc);

        chatService.addMessageToContext(currentSession.getId(), requestDTO.prompt(), Author.USER);

        String correlationId = UUID.randomUUID().toString();
        KafkaRequestDTO kafkaRequestDTO = KafkaRequestDTO.builder()
                .correlationId(correlationId)
                .chatSessionId(currentSession.getId())
                .chatMessageRequest(requestDTO)
                .build();

        CompletableFuture<ChatMessageResponseDTO> future = new CompletableFuture<>();
        pendingRequestsService.register(correlationId, future);

        log.info("Sending request {} to Kafka for Chat Session {}", correlationId, currentSession.getId());
        kafkaTemplate.send(KafkaTopicConfig.REQUEST_TOPIC, correlationId, kafkaRequestDTO);

        return future;
    }
}
