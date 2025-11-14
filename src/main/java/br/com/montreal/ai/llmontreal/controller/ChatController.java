package br.com.montreal.ai.llmontreal.controller;

import br.com.montreal.ai.llmontreal.dto.OllamaRequestDTO;
import br.com.montreal.ai.llmontreal.dto.ChatMessageResponseDTO;
import br.com.montreal.ai.llmontreal.service.ollama.OllamaProducerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import javax.validation.Valid;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatController {

    private final OllamaProducerService ollamaProducerService;

    @PostMapping("/{documentId}")
    public Mono<ChatMessageResponseDTO> sendMessageToOllama(
            @RequestBody @Valid OllamaRequestDTO requestDTO,
            @PathVariable Long documentId
    ) {
        CompletableFuture<ChatMessageResponseDTO> responseFuture = ollamaProducerService.processMessage(requestDTO, documentId);
        return Mono.fromFuture(responseFuture);
    }
}
