package br.com.montreal.ai.llmontreal.dto.kafka;

import br.com.montreal.ai.llmontreal.dto.OllamaRequestDTO;
import lombok.Builder;

@Builder
public record KafkaChatRequestDTO(
        String correlationId,
        Long chatSessionId,
        OllamaRequestDTO chatMessageRequest
) {
}
