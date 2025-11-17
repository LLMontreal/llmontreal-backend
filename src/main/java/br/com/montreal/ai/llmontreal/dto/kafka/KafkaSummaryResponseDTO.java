package br.com.montreal.ai.llmontreal.dto.kafka;

import lombok.Builder;

@Builder
public record KafkaSummaryResponseDTO(
        String correlationId,
        Long documentId,
        String summary,
        String modelName,
        boolean error,
        String errorMessage
) {
}
