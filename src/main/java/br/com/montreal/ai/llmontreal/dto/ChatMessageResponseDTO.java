package br.com.montreal.ai.llmontreal.dto;

import br.com.montreal.ai.llmontreal.entity.enums.Author;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record ChatMessageResponseDTO(
        Long documentId,
        Long chatSessionId,
        Author author,
        LocalDateTime createdAt,
        String response
) {
}
