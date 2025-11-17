package br.com.montreal.ai.llmontreal.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder
public record OllamaRequestDTO(
        String model,
        @NotBlank(message = "Prompt can not be null") String prompt,
        Boolean stream
) {
    public OllamaRequestDTO(String model, String prompt, Boolean stream) {
        this.model = model;
        this.prompt = prompt;
        this.stream = false;
    }
}
