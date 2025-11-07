package br.com.montreal.ai.llmontreal.service;

import br.com.montreal.ai.llmontreal.dto.KafkaResponseDTO;
import br.com.montreal.ai.llmontreal.dto.ChatMessageResponseDTO;
import br.com.montreal.ai.llmontreal.exception.OllamaException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;

@Component
public class PendingRequestsService {

    @Value("${spring.ai.ollama.request-timeout-ms}")
    private long expirationTime;

    private final ConcurrentHashMap<String, CompletableFuture<ChatMessageResponseDTO>> pending = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> timestamps = new ConcurrentHashMap<>();

    public void register(String correlationId, CompletableFuture<ChatMessageResponseDTO> future) {
        pending.put(correlationId, future);
        timestamps.put(correlationId, System.currentTimeMillis());
    }

    public void complete(KafkaResponseDTO responseDTO) {
        String correlationId = responseDTO.correlationId();
        CompletableFuture<ChatMessageResponseDTO> future = pending.remove(correlationId);
        timestamps.remove(correlationId);

        if (responseDTO.error()) {
            future.completeExceptionally(new OllamaException(responseDTO.errorMessage(), null));
        }

        future.complete(responseDTO.chatMessageResponseDTO());
    }

    @Scheduled(fixedRate = 30000)
    public void cleanUpExpired() {
        long now = System.currentTimeMillis();
        List<String> expiredIds = new ArrayList<>();

        timestamps.forEach((correlationId, timestamp) -> {
            if (now - timestamp > expirationTime) expiredIds.add(correlationId);
        });

        expiredIds.forEach(correlationId -> {
            timestamps.remove(correlationId);
            CompletableFuture<ChatMessageResponseDTO> future = pending.remove(correlationId);
            if (future != null) future.completeExceptionally(new TimeoutException("Request expired"));
        });
    }
}
