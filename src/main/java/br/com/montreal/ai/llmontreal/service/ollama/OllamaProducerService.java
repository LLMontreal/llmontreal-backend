package br.com.montreal.ai.llmontreal.service.ollama;

import br.com.montreal.ai.llmontreal.config.KafkaTopicConfig;
import br.com.montreal.ai.llmontreal.dto.kafka.KafkaChatRequestDTO;
import br.com.montreal.ai.llmontreal.dto.OllamaRequestDTO;
import br.com.montreal.ai.llmontreal.dto.ChatMessageResponseDTO;
import br.com.montreal.ai.llmontreal.dto.kafka.KafkaSummaryRequestDTO;
import br.com.montreal.ai.llmontreal.dto.kafka.KafkaSummaryResponseDTO;
import br.com.montreal.ai.llmontreal.entity.ChatSession;
import br.com.montreal.ai.llmontreal.entity.Document;
import br.com.montreal.ai.llmontreal.entity.enums.Author;
import br.com.montreal.ai.llmontreal.exception.SummarizeException;
import br.com.montreal.ai.llmontreal.repository.DocumentRepository;
import br.com.montreal.ai.llmontreal.service.ChatService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class OllamaProducerService {

    private final ChatService chatService;
    private final DocumentRepository documentRepository;

    private final KafkaTemplate<String, KafkaChatRequestDTO> kafkaChatTemplate;
    private final KafkaTemplate<String, KafkaSummaryRequestDTO> kafkaSummaryTemplate;

    private final PendingRequestsService pendingRequestsService;

    private static final Logger log = LoggerFactory.getLogger(OllamaProducerService.class);

    public CompletableFuture<ChatMessageResponseDTO> processMessage(
            OllamaRequestDTO requestDTO,
            Long documentId,
            String correlationId
    ) {
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new EntityNotFoundException("Document not found by id: " + documentId));

        ChatSession currentSession = chatService.getOrCreateSession(requestDTO.model(), doc);

        chatService.addMessageToContext(currentSession.getId(), requestDTO.prompt(), Author.USER);

        KafkaChatRequestDTO kafkaChatRequestDTO = KafkaChatRequestDTO.builder()
                .correlationId(correlationId)
                .chatSessionId(currentSession.getId())
                .chatMessageRequest(requestDTO)
                .build();

        CompletableFuture<ChatMessageResponseDTO> future = new CompletableFuture<>();
        pendingRequestsService.registerChat(correlationId, future);

        log.info("Sending request {} to Kafka for Chat Session {}", correlationId, currentSession.getId());
        kafkaChatTemplate.send(KafkaTopicConfig.CHAT_REQUEST_TOPIC, correlationId, kafkaChatRequestDTO);

        return future;
    }

    public CompletableFuture<KafkaSummaryResponseDTO> sendSummarizeRequest(Document document, String correlationId) {
        Document doc = documentRepository.findById(document.getId())
                .orElseThrow(() -> new EntityNotFoundException("Document not found by id: " + document.getId()));

        String content = doc.getExtractedContent();

        if (content.trim().isEmpty()) {
            throw new SummarizeException("Content must not be empty or blank");
        }

        KafkaSummaryRequestDTO kafkaSummaryRequestDTO = KafkaSummaryRequestDTO.builder()
                .correlationId(correlationId)
                .documentId(doc.getId())
                .build();

        CompletableFuture<KafkaSummaryResponseDTO> future = new CompletableFuture<>();
        pendingRequestsService.registerSummary(correlationId, future);

        log.info("Sending request {} to Kafka to Summarize Document {} content", correlationId, doc.getId());
        kafkaSummaryTemplate.send(KafkaTopicConfig.SUMMARY_REQUEST_TOPIC, correlationId, kafkaSummaryRequestDTO);

        return future;
    }
}
