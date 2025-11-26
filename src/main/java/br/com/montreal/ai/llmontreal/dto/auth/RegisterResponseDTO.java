package br.com.montreal.ai.llmontreal.dto.auth;

public record RegisterResponseDTO(
        String token,
        UserDTO user) {
    public record UserDTO(
            Long id,
            String name,
            String email) {
    }
}
