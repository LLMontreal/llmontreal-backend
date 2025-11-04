package br.com.montreal.ai.llmontreal.config;

import br.com.montreal.ai.llmontreal.dto.OllamaRequestDTO;
import br.com.montreal.ai.llmontreal.dto.OllamaResponseDTO;
import br.com.montreal.ai.llmontreal.exception.OllamaException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class OllamaWarmup {

    private static final Logger logger = LoggerFactory.getLogger(OllamaWarmup.class);

    private final WebClient webClient;

    @Value("${ollama.api.model}")
    private String defaultModel;

    @Value("${spring.ai.ollama.base-url}")
    private String ollamaApiUrl;


    @EventListener(ApplicationReadyEvent.class)
    public void warmUpModel() {
        logger.info("Warming up Ollama model: {}", defaultModel);

        try {
            OllamaRequestDTO warmupRequest = new OllamaRequestDTO(defaultModel, "hello", false);

            webClient.post()
                    .uri("/api/generate")
                    .body(Mono.just(warmupRequest), OllamaRequestDTO.class)
                    .retrieve()
                    .bodyToMono(OllamaResponseDTO.class)
                    .timeout(Duration.ofMinutes(2))
                    .doOnSuccess(res -> logger.info("Ollama model warmed up successfully."))
                    .doOnError(WebClientResponseException.class, ex -> {
                        logger.error("Failed to warm up Ollama model: {}. Status: {}, Response: {}",
                                defaultModel, ex.getStatusCode(), ex.getResponseBodyAsString());
                    })
                    .doOnError(e -> !(e instanceof WebClientResponseException), e -> {
                        logger.error("An unexpected error occurred while warming up model: {}", defaultModel, e);
                    })
                    .block();

        } catch (Exception e) {
            throw new OllamaException("Failed to warmup model", e.getCause());
        }
    }

}
