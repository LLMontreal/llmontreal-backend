package br.com.montreal.ai.llmontreal.service.ollama;

import br.com.montreal.ai.llmontreal.config.KafkaTopicConfig;
import br.com.montreal.ai.llmontreal.dto.*;
import br.com.montreal.ai.llmontreal.entity.ChatMessage;
import br.com.montreal.ai.llmontreal.entity.enums.Author;
import br.com.montreal.ai.llmontreal.exception.OllamaException;
import br.com.montreal.ai.llmontreal.repository.ChatSessionRepository;
import br.com.montreal.ai.llmontreal.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class ChatConsumerService {

    private final WebClient webClient;
    private final ChatService chatService;
    private final KafkaTemplate<String, KafkaResponseDTO> kafkaTemplate;
    private final ChatSessionRepository chatSessionRepository;

    private static final Logger log = LoggerFactory.getLogger(ChatConsumerService.class);

    @KafkaListener(topics = KafkaTopicConfig.CHAT_REQUEST_TOPIC, groupId = "ollama-processors-group")
    public void sendChatMessage(KafkaRequestDTO kafkaRequestDTO) {
        String correlationId = kafkaRequestDTO.correlationId();
        Long sessionId = kafkaRequestDTO.chatSessionId();
        OllamaRequestDTO ollamaRequestDTO = kafkaRequestDTO.chatMessageRequest();

        log.info("Received Kafka request {} for session {}. Calling model {}",
                correlationId, sessionId, ollamaRequestDTO.model());

        try {
            OllamaApiResponseDTO ollamaResponse = webClient.post()
                    .uri("/api/generate")
                    .body(Mono.just(ollamaRequestDTO), OllamaApiResponseDTO.class)
                    .retrieve()
                    .bodyToMono(OllamaApiResponseDTO.class)
                    .timeout(Duration.ofMinutes(2))
                    .block();

            if (ollamaResponse == null) {
                throw new OllamaException("Ollama error: response is null");
            }

            log.info("Ollama success for {}. Saving model response.", correlationId);

            ChatMessage chatMessage = chatService
                    .addMessageToContext(sessionId, ollamaResponse.response(), Author.MODEL);

            ChatMessageResponseDTO chatMessageResponseDTO = ChatMessageResponseDTO.builder()
                    .documentId(chatMessage.getChatSession().getDocument().getId())
                    .chatSessionId(sessionId)
                    .author(chatMessage.getAuthor())
                    .createdAt(chatMessage.getCreatedAt())
                    .response(chatMessage.getMessage())
                    .build();

            KafkaResponseDTO kafkaResponseDTO = KafkaResponseDTO.builder()
                    .correlationId(correlationId)
                    .chatMessageResponseDTO(chatMessageResponseDTO)
                    .error(false)
                    .errorMessage(null)
                    .build();

            kafkaTemplate.send(KafkaTopicConfig.CHAT_RESPONSE_TOPIC, correlationId, kafkaResponseDTO);
        } catch (Exception e) {
            log.error("Ollama call failed for {}: {}", correlationId, e.getMessage());

            String errorMsg;
            if (e instanceof WebClientResponseException ex) {
                errorMsg = ex.getResponseBodyAsString();
            } else {
                errorMsg = e.getMessage();
            }

            KafkaResponseDTO kafkaResponseDTO = KafkaResponseDTO.builder()
                    .correlationId(correlationId)
                    .chatMessageResponseDTO(null)
                    .error(true)
                    .errorMessage(errorMsg)
                    .build();

            kafkaTemplate.send(KafkaTopicConfig.CHAT_RESPONSE_TOPIC, correlationId, kafkaResponseDTO);

        }
    }
}
