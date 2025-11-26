package br.com.montreal.ai.llmontreal.controller;

import br.com.montreal.ai.llmontreal.dto.OllamaRequestDTO;
import br.com.montreal.ai.llmontreal.dto.ChatMessageResponseDTO;
import br.com.montreal.ai.llmontreal.service.DocumentService;
import br.com.montreal.ai.llmontreal.service.ollama.OllamaProducerService;
import jakarta.servlet.http.HttpServletRequest;
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
    private final DocumentService documentService;

    @PostMapping("/{documentId}")
    public Mono<ChatMessageResponseDTO> sendMessageToOllama(
            @RequestBody @Valid OllamaRequestDTO requestDTO,
            @PathVariable Long documentId,
            HttpServletRequest request
    ) {
        // Validar ownership antes de processar chat
        documentService.validateDocumentOwnership(documentId);

        String correlationId = (String) request.getAttribute("requestId");
        CompletableFuture<ChatMessageResponseDTO> responseFuture =
                ollamaProducerService.sendChatRequest(requestDTO, documentId, correlationId);
        return Mono.fromFuture(responseFuture);
    }
}
