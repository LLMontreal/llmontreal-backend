package br.com.montreal.ai.llmontreal.config;

import br.com.montreal.ai.llmontreal.dto.OllamaApiResponseDTO;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@TestConfiguration
@Profile("test")
public class TestOllamaConfig {

    @Bean
    @Primary
    public WebClient testWebClient() {
        WebClient webClient = mock(WebClient.class);
        WebClient.RequestBodyUriSpec requestBodyUriSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec requestBodySpec = mock(WebClient.RequestBodySpec.class);
        WebClient.RequestHeadersSpec requestHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        OllamaApiResponseDTO mockResponse = new OllamaApiResponseDTO(
                "deepseek-r1:1.5b",
                LocalDateTime.now().toString(),
                "Esta é uma resposta mockada do Ollama para testes de integração.",
                true
        );

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(OllamaApiResponseDTO.class))
                .thenReturn(Mono.just(mockResponse));

        return webClient;
    }
}
