package br.com.montreal.ai.llmontreal.exception;

public record ErrorResponseDTO(
        Integer status,
        String error,
        String errorMessage,
        String path
) {
    
}
