package br.com.montreal.ai.llmontreal.repository;

import br.com.montreal.ai.llmontreal.entity.OllamaLogApiCall;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OllamaLogApiCallRepository extends JpaRepository<OllamaLogApiCall, Long> {
    Optional<OllamaLogApiCall> findByCorrelationId(String correlationId);

    @Modifying
    @Query("UPDATE OllamaLogApiCall log " +
            "SET log.jobStatusCode = :jobStatus, log.jobLatencyMs = :jobLatency, log.jobErrorMessage = :errorMsg " +
            "WHERE log.correlationId = :correlationId")
    void updateJobStatus(@Param("correlationId") String correlationId,
                         @Param("jobLatency") long jobLatency,
                         @Param("jobStatus") int jobStatus,
                         @Param("errorMsg") String errorMsg);

    @Modifying
    @Query("UPDATE OllamaLogApiCall log " +
            "SET log.statusCode = :apiStatus, log.latencyMs = :apiLatency " +
            "WHERE log.correlationId = :correlationId")
    void updateApiStatus(@Param("correlationId") String correlationId,
                         @Param("apiLatency") long apiLatency,
                         @Param("apiStatus") int apiStatus);
}
