package br.com.montreal.ai.llmontreal.dto;

import lombok.Builder;

@Builder
public record KafkaResponseDTO(
        String correlationId,
        ChatMessageResponseDTO chatMessageResponseDTO,
        boolean error,
        String errorMessage
) {
}
