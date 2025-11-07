package br.com.montreal.ai.llmontreal.dto;

public record OllamaApiResponseDTO(
        String model,
        String createdAt,
        String response,
        boolean done
) {
}
