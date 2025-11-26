package br.com.montreal.ai.llmontreal.service;

import br.com.montreal.ai.llmontreal.dto.auth.LoginRequestDTO;
import br.com.montreal.ai.llmontreal.dto.auth.LoginResponseDTO;
import br.com.montreal.ai.llmontreal.dto.auth.RegisterRequestDTO;
import br.com.montreal.ai.llmontreal.dto.auth.RegisterResponseDTO;
import br.com.montreal.ai.llmontreal.entity.User;
import br.com.montreal.ai.llmontreal.entity.enums.Role;
import br.com.montreal.ai.llmontreal.exception.auth.DuplicateUserException;
import br.com.montreal.ai.llmontreal.repository.UserRepository;
import br.com.montreal.ai.llmontreal.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    @Transactional
    public RegisterResponseDTO register(RegisterRequestDTO request) {
        log.info("Registering new user: {}", request.username());

        if (userRepository.existsByUsernameAndDeletedAtIsNull(request.username())) {
            throw new DuplicateUserException("Username já está em uso");
        }

        if (userRepository.existsByEmailAndDeletedAtIsNull(request.email())) {
            throw new DuplicateUserException("Email já está em uso");
        }

        User user = User.builder()
                .username(request.username())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .role(Role.USER)
                .enabled(true)
                .createdAt(LocalDateTime.now())
                .build();

        User savedUser = userRepository.save(user);
        String jwtToken = jwtService.generateToken(savedUser);

        log.info("User registered successfully: ID={}, username={}", savedUser.getId(), savedUser.getUsername());

        RegisterResponseDTO.UserDTO userDTO = new RegisterResponseDTO.UserDTO(
                savedUser.getId(),
                savedUser.getUsername(),
                savedUser.getEmail());

        return new RegisterResponseDTO(jwtToken, userDTO);
    }

    public LoginResponseDTO login(LoginRequestDTO request) {
        log.info("User login attempt: {}", request.username());

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.username(),
                        request.password()));

        User user = (User) authentication.getPrincipal();
        String jwtToken = jwtService.generateToken(user);

        log.info("User logged in successfully: {}", user.getUsername());

        LoginResponseDTO.UserDTO userDTO = new LoginResponseDTO.UserDTO(
                user.getId(),
                user.getUsername(),
                user.getEmail());

        return new LoginResponseDTO(jwtToken, userDTO);
    }
}
