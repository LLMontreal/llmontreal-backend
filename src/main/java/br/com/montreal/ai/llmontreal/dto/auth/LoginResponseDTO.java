package br.com.montreal.ai.llmontreal.dto.auth;

public record LoginResponseDTO(
    String token,
    String username,
    String email,
    String role
) {}
