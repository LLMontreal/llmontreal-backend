package br.com.montreal.ai.llmontreal.dto.auth;

public record RegisterResponseDTO(
    Long id,
    String username,
    String email,
    String role,
    String message
) {}
