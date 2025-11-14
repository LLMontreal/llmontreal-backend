package br.com.montreal.ai.llmontreal.dto.kafka;

import br.com.montreal.ai.llmontreal.dto.ChatMessageResponseDTO;
import lombok.Builder;

@Builder
public record KafkaChatResponseDTO(
        String correlationId,
        ChatMessageResponseDTO chatMessageResponseDTO,
        boolean error,
        String errorMessage
) {
}
