package br.com.montreal.ai.llmontreal.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
public class WebClientConfig {

    @Value("${spring.ai.ollama.base-url}")
    private String ollamaUrl;

    @Value("${webclient.request.timeout-ms}")
    private long requestTimeoutMs;

    @Bean
    public WebClient webClient(WebClient.Builder builder) {
        HttpClient http = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) requestTimeoutMs)
                .responseTimeout(Duration.ofMillis(requestTimeoutMs))
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(requestTimeoutMs, TimeUnit.MILLISECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(requestTimeoutMs, TimeUnit.MILLISECONDS))
                );

        return builder.
                // baseUrl set to ollama by default, overwrite it passing the full URL to .uri() method
                baseUrl(ollamaUrl)
                .clientConnector(new ReactorClientHttpConnector(http))
                .build();
    }
}

