package br.com.montreal.ai.llmontreal.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    public static final String REQUEST_TOPIC = "chat_requests";
    public static final String RESPONSE_TOPIC = "chat_responses";

    @Bean
    public NewTopic ollamaRequestTopic() {
        return TopicBuilder.name(REQUEST_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic ollamaResponseTopic() {
        return TopicBuilder.name(RESPONSE_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
