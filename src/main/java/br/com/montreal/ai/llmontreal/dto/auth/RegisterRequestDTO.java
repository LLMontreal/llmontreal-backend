package br.com.montreal.ai.llmontreal.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequestDTO(
    @NotBlank(message = "Username é obrigatório")
    @Size(min = 3, max = 50, message = "Username deve ter entre 3 e 50 caracteres")
    String username,

    @NotBlank(message = "Email é obrigatório")
    @Email(message = "Email deve ser válido")
    String email,

    @NotBlank(message = "Password é obrigatório")
    @Size(min = 6, message = "Password deve ter no mínimo 6 caracteres")
    String password
) {}
