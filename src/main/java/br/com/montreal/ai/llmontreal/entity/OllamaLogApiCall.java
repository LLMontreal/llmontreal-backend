package br.com.montreal.ai.llmontreal.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "log_api_calls")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OllamaLogApiCall {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    String correlationId;

    @Column(name = "event_timestamp")
    private Instant timestamp;

    private Long latencyMs;

    private String endpoint;

    private Integer statusCode;

    private String ipAddress;

    @Column(name = "job_latency_ms")
    private Long jobLatencyMs;

    @Column(name = "job_status_code")
    private Integer jobStatusCode;

    @Column(name = "job_error_message")
    private String jobErrorMessage;

}
