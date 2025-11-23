package br.com.montreal.ai.llmontreal.service;

import br.com.montreal.ai.llmontreal.entity.Document;
import br.com.montreal.ai.llmontreal.entity.enums.DocumentStatus;
import br.com.montreal.ai.llmontreal.event.DocumentExtractionCompletedEvent;
import br.com.montreal.ai.llmontreal.exception.ExtractionException;
import br.com.montreal.ai.llmontreal.repository.DocumentRepository;
import br.com.montreal.ai.llmontreal.service.extraction.ContentExtractor;
import br.com.montreal.ai.llmontreal.service.ollama.OllamaProducerService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentExtractionService {

    private final List<ContentExtractor> extractors;
    private final DocumentRepository documentRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final OllamaProducerService ollamaProducerService;

    @Async("documentExtractionExecutor")
    public void extractContentAsync(Long documentId, String correlationId) {
        log.info("Starting extraction of document with id {}", documentId);
        long startTime = System.currentTimeMillis();

        try {
            Document document = documentRepository.findById(documentId)
                    .orElseThrow(() -> new EntityNotFoundException("Document with id " + documentId + " not found"));

            log.info("Processing document: {} (type: {})", document.getFileName(), document.getFileType());

            document.setStatus(DocumentStatus.PROCESSING);

            String extractedContent = extractContent(
                    document.getFileData(),
                    document.getFileType()
            );

            long duration = System.currentTimeMillis() - startTime;

            if(extractedContent == null || extractedContent.isBlank()) {
                log.warn("Extraction for document {} returned empty content.", documentId);

                document.setStatus(DocumentStatus.FAILED);

                eventPublisher.publishEvent(
                    DocumentExtractionCompletedEvent.failure(
                            this,
                            documentId,
                            "Nenhum conteúdo pôde ser extraído do documento."
                    )
                );
                return;
            }

            log.info("Extraction completed successfully for document {} in {}ms. Content length: {} characters",
                    documentId, duration, extractedContent != null ? extractedContent.length() : 0);

            eventPublisher.publishEvent(
                    DocumentExtractionCompletedEvent.success(this, documentId, extractedContent)
            );

            ollamaProducerService.sendSummarizeRequest(document, correlationId);
        } catch (ExtractionException e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Extraction failed for document {} after {}ms: {}", documentId, duration, e.getMessage());

            eventPublisher.publishEvent(
                    DocumentExtractionCompletedEvent.failure(this, documentId, e.getMessage())
            );

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Unexpected error during extraction for document {} after {}ms", documentId, duration, e);

            eventPublisher.publishEvent(
                    DocumentExtractionCompletedEvent.failure(this, documentId,
                            "Unexpected error: " + e.getMessage())
            );
        }
    }

    private String extractContent(byte[] fileData, String contentType) throws ExtractionException {
        Optional<ContentExtractor> extractor = findExtractor(contentType);

        if (extractor.isEmpty()) {
            throw new ExtractionException("No extractor found for content type " + contentType);
        }

        try (InputStream inputStream = new ByteArrayInputStream(fileData)) {
            ContentExtractor selectedExtractor = extractor.get();

            return selectedExtractor.extractContent(inputStream, contentType);
        } catch (ExtractionException e) {
            throw e;
        } catch (Exception e) {
            throw new ExtractionException("Extraction failed: " + e.getMessage());
        }
    }

    private Optional<ContentExtractor> findExtractor(String contentType) {
        return extractors.stream()
                .filter(ex -> ex.supportsThisContentType(contentType))
                .findFirst();
    }

    public String extractContentSync(Long documentId) throws ExtractionException {
        log.info("Starting synchronous extraction of document with id {}", documentId);
        long startTime = System.currentTimeMillis();

        try {
            Document document = documentRepository.findById(documentId)
                    .orElseThrow(() -> new EntityNotFoundException("Document with id " + documentId + " not found"));

            document.setStatus(DocumentStatus.PROCESSING);
            documentRepository.save(document);

            String extractedContent = extractContent(
                    document.getFileData(),
                    document.getFileType()
            );

            long duration = System.currentTimeMillis() - startTime;

            if(extractedContent == null || extractedContent.isBlank()) {
                log.warn("Extraction for document {} returned empty content.", documentId);
                document.setStatus(DocumentStatus.FAILED);
                documentRepository.save(document);
                throw new ExtractionException("Nenhum conteúdo pôde ser extraído do documento.");
            }

            log.info("Extraction completed successfully for document {} in {}ms. Content length: {} characters",
                    documentId, duration, extractedContent != null ? extractedContent.length() : 0);

            document.setExtractedContent(extractedContent);
            documentRepository.save(document);

            return extractedContent;

        } catch (ExtractionException e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Extraction failed for document {} after {}ms: {}", documentId, duration, e.getMessage());

            Document document = documentRepository.findById(documentId).orElse(null);
            if (document != null) {
                document.setStatus(DocumentStatus.FAILED);
                documentRepository.save(document);
            }

            throw e;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Unexpected error during extraction for document {} after {}ms", documentId, duration, e);

            Document document = documentRepository.findById(documentId).orElse(null);
            if (document != null) {
                document.setStatus(DocumentStatus.FAILED);
                documentRepository.save(document);
            }

            throw new ExtractionException("Unexpected error: " + e.getMessage());
        }
    }
}
