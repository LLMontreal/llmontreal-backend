package br.com.montreal.ai.llmontreal.config;

import br.com.montreal.ai.llmontreal.entity.OllamaLogApiCall;
import br.com.montreal.ai.llmontreal.service.ollama.OllamaLogApiCallService;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class ApiLoggingFilter implements Filter {

    private final OllamaLogApiCallService logApiCallService;

    private static final String UNKNOWN = "unknown";
    private static final String CHAT_ENDPOINT = "/api/chat";
    private static final String DOCUMENT_ENDPOINT = "/api/documents";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String requestUri = httpRequest.getRequestURI();
        String requestMethod = httpRequest.getMethod();

        if (!shouldCreateLog(requestUri, requestMethod)) {
            chain.doFilter(request, response);
            return;
        }

        String correlationId = getOrGenerateCorrelationId(httpRequest);
        httpRequest.setAttribute("requestId", correlationId);

        long startTime = System.currentTimeMillis();
        httpRequest.setAttribute("startTime", startTime);

        createInitialLog(correlationId, httpRequest, requestMethod, requestUri);

        handleResponseWrapper(requestUri, correlationId, chain, httpRequest, httpResponse, startTime);
    }

    private String getRequestIpAddress(HttpServletRequest req) {
        String ip = req.getHeader("X-Forwarded-For");

        if (ip == null || ip.isEmpty() || UNKNOWN.equalsIgnoreCase(ip)) {
            ip = req.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || UNKNOWN.equalsIgnoreCase(ip)) {
            ip = req.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || UNKNOWN.equalsIgnoreCase(ip)) {
            ip = req.getRemoteAddr();
        }

        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }

        return ip;
    }

    private boolean shouldCreateLog(String requestUri, String requestMethod) {
        boolean shouldLog = requestUri.startsWith(CHAT_ENDPOINT);

        if (requestUri.startsWith(DOCUMENT_ENDPOINT) && "POST".equalsIgnoreCase(requestMethod)) {
            shouldLog = true;
        }
        return shouldLog;
    }

    private String getOrGenerateCorrelationId(HttpServletRequest request) {
        String correlationId = request.getHeader("X-Request-ID");

        if (correlationId == null) {
            correlationId = UUID.randomUUID().toString();
            log.info("Generated correlation ID: {}", correlationId);
        }

        return correlationId;
    }

    private void createInitialLog(
            String correlationId,
            HttpServletRequest request,
            String requestMethod,
            String requestUri
    ) {
        OllamaLogApiCall logApiCall = OllamaLogApiCall.builder()
                .correlationId(correlationId)
                .ipAddress(getRequestIpAddress(request))
                .endpoint(requestUri)
                .timestamp(Instant.now())
                .statusCode(0)
                .latencyMs(0L)
                .build();

        log.info("Creating initial log for {} {} - ID: {}", requestMethod, requestUri, correlationId);
        logApiCallService.saveLogApiCall(logApiCall);
    }

    private void handleResponseWrapper(
            String requestUri,
            String correlationId,
            FilterChain chain,
            HttpServletRequest request,
            HttpServletResponse response,
            long startTime
    ) throws ServletException, IOException {
        if (requestUri.startsWith(CHAT_ENDPOINT)) {
            try {
                chain.doFilter(request, response);
            } finally {
                long latency = System.currentTimeMillis() - startTime;
                int statusCode = response.getStatus();

                log.info("Updating initial HTTP status for chat {}: status={}, latency={}ms",
                        correlationId, statusCode, latency);
                logApiCallService.updateApiCallLogWithApiResult(correlationId, latency, statusCode);
            }
            return;
        }

        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);

        try {
            chain.doFilter(request, responseWrapper);
        } finally {
            long latency = System.currentTimeMillis() - startTime;
            int statusCode = responseWrapper.getStatus();

            log.info("Updating API metrics for {}: status={}, latency={}ms", correlationId, statusCode, latency);
            logApiCallService.updateApiCallLogWithApiResult(correlationId, latency, statusCode);

            responseWrapper.copyBodyToResponse();
        }
    }
}