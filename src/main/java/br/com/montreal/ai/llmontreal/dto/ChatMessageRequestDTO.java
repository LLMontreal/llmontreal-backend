package br.com.montreal.ai.llmontreal.dto;

import lombok.Builder;

@Builder
public record ChatMessageRequestDTO(String model, String prompt, Boolean stream) {
}
