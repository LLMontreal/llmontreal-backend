package br.com.montreal.ai.llmontreal.service.ollama;

import br.com.montreal.ai.llmontreal.entity.OllamaLogApiCall;
import br.com.montreal.ai.llmontreal.repository.OllamaLogApiCallRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class OllamaLogApiCallService {

    private final OllamaLogApiCallRepository logApiCallRepository;

    @Async("logApiCallExecutor")
    public void saveLogApiCall(OllamaLogApiCall logApiCall) {
        try {
            String id = logApiCall.getCorrelationId() != null ?
                    logApiCall.getCorrelationId() : "NO_ID";

            log.info("Saving log for request: {}", id);

            logApiCallRepository.save(logApiCall);

            log.info("Successfully saved log for request: {}", id);

        } catch (Exception e) {
            log.error("Failed to save log for request: {}. Error: {}",
                    logApiCall.getCorrelationId(), e.getMessage());
        }
    }

    @Async("logApiCallExecutor")
    public void updateApiCallLog(String correlationId, long jobLatency, int jobStatusCode, String errorMsg) {
        try {
            logApiCallRepository.updateJobStatus(correlationId, jobLatency, jobStatusCode, errorMsg);
            log.info("Successfully updated JOB status for request: {}", correlationId);
        } catch (Exception e) {
            log.error("Failed to update JOB log for {}: {}", correlationId, e.getMessage(), e);
        }
    }

    @Async("logApiCallExecutor")
    public void updateApiCallLogWithApiResult(String correlationId, long apiLatency, int apiStatusCode) {
        try {
            logApiCallRepository.updateApiStatus(correlationId, apiLatency, apiStatusCode);
            log.info("Successfully updated API status for request: {}", correlationId);
        } catch (Exception e) {
            log.error("Failed to update API log for {}: {}", correlationId, e.getMessage(), e);
        }
    }
}
