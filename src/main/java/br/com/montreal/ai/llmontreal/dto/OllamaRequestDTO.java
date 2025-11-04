package br.com.montreal.ai.llmontreal.dto;

public record OllamaRequestDTO(String model, String prompt, Boolean stream) {
}
