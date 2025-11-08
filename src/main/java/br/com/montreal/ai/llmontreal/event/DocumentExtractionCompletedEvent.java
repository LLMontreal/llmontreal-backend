package br.com.montreal.ai.llmontreal.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class DocumentExtractionCompletedEvent extends ApplicationEvent {

    private final Long documentId;
    private final String extractedContent;
    private final boolean success;
    private final String errorMessage;

    public DocumentExtractionCompletedEvent(Object source, Long documentId, String extractedContent,
                                            boolean success, String errorMessage) {
        super(source);
        this.documentId = documentId;
        this.extractedContent = extractedContent;
        this.success = success;
        this.errorMessage = errorMessage;
    }

    public static DocumentExtractionCompletedEvent success(Object source, Long documentId,
                                                           String extractedContent) {
        return new DocumentExtractionCompletedEvent(source, documentId, extractedContent, true, null);
    }

    public static DocumentExtractionCompletedEvent failure(Object source, Long documentId,
                                                           String errorMessage) {
        return new DocumentExtractionCompletedEvent(source, documentId, null, false, errorMessage);
    }
}
