package br.com.montreal.ai.llmontreal.dto.auth;

public record LoginResponseDTO(
        String token,
        UserDTO user) {
    public record UserDTO(
            Long id,
            String name,
            String email) {
    }
}
