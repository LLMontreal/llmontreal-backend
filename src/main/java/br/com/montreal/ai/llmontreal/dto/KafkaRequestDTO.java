package br.com.montreal.ai.llmontreal.dto;

import lombok.Builder;

@Builder
public record KafkaRequestDTO(
        String correlationId,
        Long chatSessionId,
        OllamaRequestDTO chatMessageRequest
) {
}
