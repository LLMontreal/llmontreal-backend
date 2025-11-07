package br.com.montreal.ai.llmontreal.service;

import br.com.montreal.ai.llmontreal.config.KafkaTopicConfig;
import br.com.montreal.ai.llmontreal.dto.KafkaResponseDTO;
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

    @KafkaListener(topics = KafkaTopicConfig.RESPONSE_TOPIC, groupId = "ollama-response-group")
    public void handleOllamaResponse(KafkaResponseDTO responseDTO) {
        log.info("Received response for correlationId: {}", responseDTO.correlationId());
        pendingRequestsService.complete(responseDTO);
    }
}
