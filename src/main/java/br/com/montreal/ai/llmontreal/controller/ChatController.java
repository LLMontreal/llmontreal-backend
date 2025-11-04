package br.com.montreal.ai.llmontreal.controller;

import br.com.montreal.ai.llmontreal.dto.OllamaRequestDTO;
import br.com.montreal.ai.llmontreal.dto.OllamaResponseDTO;
import br.com.montreal.ai.llmontreal.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping("/{documentId}")
    public Mono<OllamaResponseDTO> sendMessageToOllama(
            @RequestBody OllamaRequestDTO requestDTO,
            @PathVariable Long documentId
    ) {
        return chatService.processMessage(requestDTO, documentId);
    }
}
