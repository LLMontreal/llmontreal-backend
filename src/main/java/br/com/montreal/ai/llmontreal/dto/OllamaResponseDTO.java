package br.com.montreal.ai.llmontreal.dto;

import java.time.LocalDateTime;

public record OllamaResponseDTO(
        Long chatSessionId,
        Long id,
        String author,
        LocalDateTime createdAt,
        String response) {
}
