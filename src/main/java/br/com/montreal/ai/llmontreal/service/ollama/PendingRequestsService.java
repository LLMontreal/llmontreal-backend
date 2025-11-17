package br.com.montreal.ai.llmontreal.service.ollama;

import br.com.montreal.ai.llmontreal.dto.kafka.KafkaChatResponseDTO;
import br.com.montreal.ai.llmontreal.dto.ChatMessageResponseDTO;
import br.com.montreal.ai.llmontreal.dto.kafka.KafkaSummaryResponseDTO;
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

    private final ConcurrentHashMap<String, CompletableFuture<ChatMessageResponseDTO>> pendingChat = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CompletableFuture<KafkaSummaryResponseDTO>> pendingSummary = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, Long> timestamps = new ConcurrentHashMap<>();

    public void registerChat(String correlationId, CompletableFuture<ChatMessageResponseDTO> future) {
        pendingChat.put(correlationId, future);
        timestamps.put(correlationId, System.currentTimeMillis());
    }

    public void registerSummary(String correlationId, CompletableFuture<KafkaSummaryResponseDTO> future) {
        pendingSummary.put(correlationId, future);
        timestamps.put(correlationId, System.currentTimeMillis());
    }

    public void completeChat(KafkaChatResponseDTO responseDTO) {
        String correlationId = responseDTO.correlationId();
        CompletableFuture<ChatMessageResponseDTO> future = pendingChat.remove(correlationId);
        timestamps.remove(correlationId);

        if (responseDTO.error()) {
            future.completeExceptionally(new OllamaException(responseDTO.errorMessage(), null));
        }

        future.complete(responseDTO.chatMessageResponseDTO());
    }

    public void completeSummary(KafkaSummaryResponseDTO responseDTO) {
        String correlationId = responseDTO.correlationId();
        CompletableFuture<KafkaSummaryResponseDTO> future = pendingSummary.remove(correlationId);
        timestamps.remove(correlationId);

        if (responseDTO.error()) {
            future.completeExceptionally(new OllamaException(responseDTO.errorMessage(), null));
        }

        future.complete(responseDTO);
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
            CompletableFuture<ChatMessageResponseDTO> futureChat = pendingChat.remove(correlationId);
            CompletableFuture<KafkaSummaryResponseDTO> futureSummary = pendingSummary.remove(correlationId);
            if (futureChat != null) {
                futureChat.completeExceptionally(new TimeoutException("Chat request expired: " + correlationId));
            }

            if (futureSummary != null) {
                futureSummary.completeExceptionally(new TimeoutException("Summary request expired: " + correlationId));
            }
        });
    }
}
