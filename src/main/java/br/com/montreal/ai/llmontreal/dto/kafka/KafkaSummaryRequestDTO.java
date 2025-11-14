package br.com.montreal.ai.llmontreal.dto.kafka;

import lombok.Builder;

@Builder
public record KafkaSummaryRequestDTO(
        String correlationId,
        Long documentId
) {
}
