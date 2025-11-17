package br.com.montreal.ai.llmontreal.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    public static final String CHAT_REQUEST_TOPIC = "chat_ollama_request";
    public static final String CHAT_RESPONSE_TOPIC = "chat_ollama_response";

    public static final String SUMMARY_REQUEST_TOPIC = "summary_ollama_request";
    public static final String SUMMARY_RESPONSE_TOPIC = "summary_ollama_response";

    @Bean
    public NewTopic chatRequestTopic() {
        return TopicBuilder.name(CHAT_REQUEST_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic chatResponseTopic() {
        return TopicBuilder.name(CHAT_RESPONSE_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic summaryRequestTopic() {
        return TopicBuilder.name(SUMMARY_REQUEST_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic summaryResponseTopic() {
        return TopicBuilder.name(SUMMARY_RESPONSE_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
