package br.com.montreal.ai.llmontreal.listener;

import br.com.montreal.ai.llmontreal.entity.Document;
import br.com.montreal.ai.llmontreal.entity.enums.DocumentStatus;
import br.com.montreal.ai.llmontreal.event.DocumentExtractionCompletedEvent;
import br.com.montreal.ai.llmontreal.repository.DocumentRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentExtractionEventListener {

    private final DocumentRepository documentRepository;

    @EventListener
    @Transactional
    public void handleExtractionCompleted(DocumentExtractionCompletedEvent event) {
        log.debug("Handling extraction completed event for document {}: success={}",
                event.getDocumentId(), event.isSuccess());

        Document document = documentRepository.findById(event.getDocumentId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Document not found with id: " + event.getDocumentId()));

        try {
            if (event.isSuccess()) {
                document.setExtractedContent(event.getExtractedContent());
                document.setStatus(DocumentStatus.COMPLETED);

                log.info("Document {} ({}) extraction completed successfully. Content length: {} characters",
                        event.getDocumentId(),
                        document.getFileName(),
                        event.getExtractedContent() != null ? event.getExtractedContent().length() : 0);

            } else {
                document.setStatus(DocumentStatus.FAILED);

                log.error("Document {} ({}) extraction failed: {}",
                        event.getDocumentId(),
                        document.getFileName(),
                        event.getErrorMessage());
            }

            documentRepository.save(document);

            log.debug("Document {} status updated to {}", event.getDocumentId(), document.getStatus());

        } catch (Exception e) {
            log.error("Error handling extraction completed event for document {}: {}",
                    event.getDocumentId(), e.getMessage(), e);
        }
    }
}