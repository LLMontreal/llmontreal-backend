package br.com.montreal.ai.llmontreal.service.extraction;

import br.com.montreal.ai.llmontreal.exception.ExtractionException;

import java.io.InputStream;

public interface ContentExtractor {

    String extractContent(InputStream inputStream, String contentType) throws ExtractionException;

    boolean supportsThisContentType(String contentType);

    default int getPriority() {
        return 100;
    }
}