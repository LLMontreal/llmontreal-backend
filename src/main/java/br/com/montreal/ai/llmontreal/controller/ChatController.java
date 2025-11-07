package br.com.montreal.ai.llmontreal.controller;

import br.com.montreal.ai.llmontreal.dto.ChatMessageRequestDTO;
import br.com.montreal.ai.llmontreal.dto.ChatMessageResponseDTO;
import br.com.montreal.ai.llmontreal.service.ChatProducerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import javax.validation.Valid;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatProducerService chatProducerService;

    @PostMapping("/{documentId}")
    public Mono<ChatMessageResponseDTO> sendMessageToOllama(
            @RequestBody @Valid ChatMessageRequestDTO requestDTO,
            @PathVariable Long documentId
    ) {
        CompletableFuture<ChatMessageResponseDTO> responseFuture = chatProducerService.processMessage(requestDTO, documentId);
        return Mono.fromFuture(responseFuture);
    }
}
