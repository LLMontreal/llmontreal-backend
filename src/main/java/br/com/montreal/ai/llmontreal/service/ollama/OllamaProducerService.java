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
import java.util.function.BiConsumer;

@Service
@RequiredArgsConstructor
public class OllamaProducerService {

    private final ChatService chatService;
    private final DocumentRepository documentRepository;

    private final KafkaTemplate<String, KafkaChatRequestDTO> kafkaChatTemplate;
    private final KafkaTemplate<String, KafkaSummaryRequestDTO> kafkaSummaryTemplate;

    private final PendingRequestsService pendingRequestsService;

    private static final Logger log = LoggerFactory.getLogger(OllamaProducerService.class);

    public CompletableFuture<ChatMessageResponseDTO> sendChatRequest(
            OllamaRequestDTO requestDTO,
            Long documentId,
            String correlationId
    ) {
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new EntityNotFoundException("Document not found by id: " + documentId));

        ChatSession currentSession = chatService.getOrCreateSession(requestDTO.model(), doc);

        chatService.addMessageToContext(currentSession.getId(), requestDTO.prompt(), Author.USER);

        String logMessage = String.format(
                "Sending request %s to Kafka for Chat Session %s", correlationId, currentSession.getId()
        );

        KafkaChatRequestDTO kafkaChatRequestDTO = KafkaChatRequestDTO.builder()
                .correlationId(correlationId)
                .chatSessionId(currentSession.getId())
                .chatMessageRequest(requestDTO)
                .build();

        return sendKafkaRequest(
                correlationId,
                KafkaTopicConfig.CHAT_REQUEST_TOPIC,
                kafkaChatRequestDTO,
                kafkaChatTemplate,
                pendingRequestsService::registerChat,
                logMessage
        );
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

        String logMessage = String.format(
                "Sending request %s to Kafka to Summarize Document %s content", correlationId, doc.getId()
        );

        return sendKafkaRequest(
                correlationId,
                KafkaTopicConfig.SUMMARY_REQUEST_TOPIC,
                kafkaSummaryRequestDTO,
                kafkaSummaryTemplate,
                pendingRequestsService::registerSummary,
                logMessage
        );
    }

    private <PayloadT, ResponseT> CompletableFuture<ResponseT> sendKafkaRequest(
            String correlationId,
            String topic,
            PayloadT payload,
            KafkaTemplate<String, PayloadT> template,
            BiConsumer<String, CompletableFuture<ResponseT>> processFunction,
            String logMessage
    ) {
        CompletableFuture<ResponseT> future = new CompletableFuture<>();
        processFunction.accept(correlationId, future);

        log.info(logMessage);

        template.send(topic, correlationId, payload);
        return future;
    }
}
