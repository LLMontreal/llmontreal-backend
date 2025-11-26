package br.com.montreal.ai.llmontreal.controller;

import br.com.montreal.ai.llmontreal.dto.auth.LoginRequestDTO;
import br.com.montreal.ai.llmontreal.dto.auth.LoginResponseDTO;
import br.com.montreal.ai.llmontreal.dto.auth.RegisterRequestDTO;
import br.com.montreal.ai.llmontreal.dto.auth.RegisterResponseDTO;
import br.com.montreal.ai.llmontreal.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<RegisterResponseDTO> register(@RequestBody @Valid RegisterRequestDTO request) {
        RegisterResponseDTO response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@RequestBody @Valid LoginRequestDTO request) {
        LoginResponseDTO response = authService.login(request);
        return ResponseEntity.ok(response);
    }
}
