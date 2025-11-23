package br.com.montreal.ai.llmontreal.service;

import br.com.montreal.ai.llmontreal.entity.OllamaLogApiCall;
import br.com.montreal.ai.llmontreal.repository.OllamaLogApiCallRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class LogDownloadService {

    private final OllamaLogApiCallRepository logRepository;

    @Transactional(readOnly = true)
    public byte[] generateLogsCsv(Instant startDate, Instant endDate) {
        log.info("Generating CSV for logs. StartDate: {}, EndDate: {}", startDate, endDate);

        List<OllamaLogApiCall> logs = fetchLogs(startDate, endDate);

        log.info("Found {} logs to export", logs.size());

        return convertLogsToCsv(logs);
    }

    private List<OllamaLogApiCall> fetchLogs(Instant startDate, Instant endDate) {
        if (startDate != null && endDate != null) {
            return logRepository.findByTimestampBetween(startDate, endDate);
        } else if (startDate != null) {
            return logRepository.findByTimestampAfter(startDate);
        } else if (endDate != null) {
            return logRepository.findByTimestampBefore(endDate);
        } else {
            return logRepository.findAll();
        }
    }

    private byte[] convertLogsToCsv(List<OllamaLogApiCall> logs) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             OutputStreamWriter osw = new OutputStreamWriter(baos, StandardCharsets.UTF_8);
             PrintWriter writer = new PrintWriter(osw)) {

            writer.println("ID,Correlation ID,Timestamp,Endpoint,Status Code,Latency (ms),IP Address,Job Status Code,Job Latency (ms),Job Error Message");

            for (OllamaLogApiCall log : logs) {
                writer.printf("%d,%s,%s,%s,%s,%s,%s,%s,%s,%s%n",
                        log.getId(),
                        escapeCsv(log.getCorrelationId()),
                        log.getTimestamp() != null ? log.getTimestamp().toString() : "",
                        escapeCsv(log.getEndpoint()),
                        log.getStatusCode() != null ? log.getStatusCode() : "",
                        log.getLatencyMs() != null ? log.getLatencyMs() : "",
                        escapeCsv(log.getIpAddress()),
                        log.getJobStatusCode() != null ? log.getJobStatusCode() : "",
                        log.getJobLatencyMs() != null ? log.getJobLatencyMs() : "",
                        escapeCsv(log.getJobErrorMessage())
                );
            }

            writer.flush();
            return baos.toByteArray();

        } catch (Exception e) {
            log.error("Error generating CSV: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate CSV file", e);
        }
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }

        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }

        return value;
    }
}

