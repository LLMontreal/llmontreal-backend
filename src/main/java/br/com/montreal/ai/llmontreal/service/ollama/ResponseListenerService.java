package br.com.montreal.ai.llmontreal.service.ollama;

import br.com.montreal.ai.llmontreal.config.KafkaTopicConfig;
import br.com.montreal.ai.llmontreal.dto.kafka.KafkaChatResponseDTO;
import br.com.montreal.ai.llmontreal.dto.kafka.KafkaSummaryResponseDTO;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ResponseListenerService {

    private static final Logger log = LoggerFactory.getLogger(ResponseListenerService.class);

    private final PendingRequestsService pendingRequestsService;

    @KafkaListener(topics = KafkaTopicConfig.CHAT_RESPONSE_TOPIC, groupId = "ollama-response-group")
    public void handleOllamaChatResponse(KafkaChatResponseDTO responseDTO) {
        log.info("Received response {} for for ChatSession {}",
                responseDTO.correlationId(), responseDTO.chatMessageResponseDTO().chatSessionId());
        pendingRequestsService.completeChat(responseDTO);
    }

    @KafkaListener(topics = KafkaTopicConfig.SUMMARY_RESPONSE_TOPIC, groupId = "ollama-response-group")
    public void handleOllamaSummarizeResponse(KafkaSummaryResponseDTO responseDTO) {
        log.info("Received response {} for document {} summarize. ",
                responseDTO.correlationId(), responseDTO.documentId());
        pendingRequestsService.completeSummary(responseDTO);
    }
}
