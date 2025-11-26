package br.com.montreal.ai.llmontreal.service;

import br.com.montreal.ai.llmontreal.config.KafkaTopicConfig;
import br.com.montreal.ai.llmontreal.dto.DocumentUploadResponse;
import br.com.montreal.ai.llmontreal.dto.kafka.KafkaSummaryRequestDTO;
import br.com.montreal.ai.llmontreal.dto.kafka.KafkaSummaryResponseDTO;
import br.com.montreal.ai.llmontreal.entity.Document;
import br.com.montreal.ai.llmontreal.entity.User;
import br.com.montreal.ai.llmontreal.entity.enums.DocumentStatus;
import br.com.montreal.ai.llmontreal.exception.FileUploadException;
import br.com.montreal.ai.llmontreal.exception.FileValidationException;
import br.com.montreal.ai.llmontreal.exception.auth.UnauthorizedAccessException;
import br.com.montreal.ai.llmontreal.repository.DocumentRepository;
import br.com.montreal.ai.llmontreal.repository.UserRepository;
import br.com.montreal.ai.llmontreal.service.ollama.OllamaProducerService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.kafka.core.KafkaTemplate;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final DocumentExtractionService extractionService;
    private final ZipProcessingService zipProcessingService;
    private final KafkaTemplate<String, KafkaSummaryRequestDTO> kafkaSummaryTemplate;
    private final OllamaProducerService ollamaProducerService;

    private static final long MAX_FILE_SIZE = 25L * 1024 * 1024;
    private static final String ZIP_CONTENT_TYPE = "application/zip";
    private static final String ZIP_CONTENT_TYPE_ALT = "application/x-zip-compressed";
    private static final List<String> ALLOWED_CONTENT_TYPES = Arrays.asList(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "image/jpeg",
            "image/png",
            ZIP_CONTENT_TYPE,
            ZIP_CONTENT_TYPE_ALT,
            "text/plain");

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return (User) authentication.getPrincipal();
    }

    public void validateDocumentOwnership(Long documentId) {
        User currentUser = getCurrentUser();
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new EntityNotFoundException("Documento não encontrado"));

        if (!document.getUser().getId().equals(currentUser.getId())) {
            throw new UnauthorizedAccessException("Você não tem permissão para acessar este documento");
        }
    }

    public Page<Document> getAllDocuments(Pageable pageable, DocumentStatus documentStatus) {
        User currentUser = getCurrentUser();

        if (documentStatus == null) {
            return documentRepository.findAllByUser(currentUser, pageable);
        }

        return documentRepository.findAllByUserAndStatus(currentUser, documentStatus, pageable);
    }

    public DocumentUploadResponse uploadFile(MultipartFile file, String correlationId) {
        validateFile(file);

        try {
            byte[] fileData = file.getBytes();
            String contentType = file.getContentType();
            String fileName = file.getOriginalFilename();

            if (ZIP_CONTENT_TYPE.equalsIgnoreCase(contentType) || ZIP_CONTENT_TYPE_ALT.equalsIgnoreCase(contentType))  {
                return processZipFile(fileData, fileName, correlationId);
            }

            return processSingleFile(fileName, contentType, fileData, correlationId);

        } catch (IOException e) {
            String errorMessage = String.format(
                    "Erro ao ler o conteúdo do arquivo: %s", file.getOriginalFilename());
            log.error(errorMessage, e);
            throw new FileUploadException(errorMessage, e);
        }
    }

    private DocumentUploadResponse processZipFile(byte[] zipData, String fileName, String correlationId) {
        log.info("Processing ZIP file: {}", fileName);

        User currentUser = getCurrentUser();
        List<Long> documentIds = zipProcessingService.processZipFile(zipData, fileName, correlationId, currentUser);

        if (documentIds.isEmpty()) {
            log.warn("No valid documents found in ZIP: {}", fileName);
            throw new FileUploadException("Nenhum arquivo válido encontrado no ZIP");
        }

        log.info("ZIP processed successfully: {} - Created {} documents", fileName, documentIds.size());

        Document firstDocument = documentRepository.findById(documentIds.get(0))
                .orElseThrow(() -> new FileUploadException("Documento criado não encontrado"));

        return new DocumentUploadResponse(firstDocument, documentIds.size(), documentIds);
    }

    private DocumentUploadResponse processSingleFile(String fileName, String contentType, byte[] fileData,
            String correlationId) {
        User currentUser = getCurrentUser();

        Document document = Document.builder()
                .fileName(fileName)
                .fileType(contentType)
                .fileData(fileData)
                .status(DocumentStatus.PENDING)
                .user(currentUser)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        Document savedDocument = documentRepository.save(document);
        log.info("Arquivo carregado com sucesso: {} (ID: {})",
                savedDocument.getFileName(), savedDocument.getId());

        try {
            extractionService.extractContentSync(savedDocument.getId());

            savedDocument = documentRepository.findById(savedDocument.getId())
                    .orElseThrow(() -> new FileUploadException("Documento não encontrado após extração"));

            CompletableFuture<KafkaSummaryResponseDTO> summaryFuture =
                    ollamaProducerService.sendSummarizeRequest(savedDocument, correlationId);

            KafkaSummaryResponseDTO summaryResponse = summaryFuture.get(10, TimeUnit.MINUTES);

            savedDocument = documentRepository.findById(savedDocument.getId())
                    .orElseThrow(() -> new FileUploadException("Documento não encontrado após geração de resumo"));
            savedDocument.setStatus(DocumentStatus.COMPLETED);
            savedDocument.setUpdatedAt(LocalDateTime.now());
            savedDocument = documentRepository.save(savedDocument);

            log.info("Processamento completo do documento ID: {} finalizado", savedDocument.getId());

            return new DocumentUploadResponse(savedDocument);

        } catch (Exception e) {
            log.error("Erro ao processar documento ID: {}", savedDocument.getId(), e);

            savedDocument = documentRepository.findById(savedDocument.getId()).orElse(savedDocument);
            savedDocument.setStatus(DocumentStatus.FAILED);
            savedDocument.setUpdatedAt(LocalDateTime.now());
            documentRepository.save(savedDocument);

            throw new FileUploadException("Erro ao processar documento: " + e.getMessage(), e);
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new FileValidationException("O arquivo não pode ser nulo ou vazio");
        }

        String fileName = file.getOriginalFilename();
        if (fileName == null || fileName.trim().isEmpty()) {
            throw new FileValidationException("Nome do arquivo inválido");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new FileValidationException(
                    String.format("O arquivo excede o tamanho máximo permitido de %d MB",
                            MAX_FILE_SIZE / (1024 * 1024)));
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new FileValidationException(
                    String.format("Tipo de arquivo não suportado: %s. Tipos aceitos: %s",
                            contentType, String.join(", ", ALLOWED_CONTENT_TYPES)));
        }

        log.debug("Arquivo validado: {} - {} - {} bytes",
                fileName, contentType, file.getSize());
    }

    public Optional<String> getExtractedContent(Long documentId) {
        validateDocumentOwnership(documentId);
        return documentRepository.findById(documentId)
                .map(Document::getExtractedContent);
    }

    public Optional<String> getSummary(Long documentId) {
        validateDocumentOwnership(documentId);
        return documentRepository.findById(documentId)
                .map(Document::getSummary);
    }

    public void regenerateSummary(Long documentId, String correlationId) {
        validateDocumentOwnership(documentId);
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new EntityNotFoundException("Documento não encontrado com id: " + documentId));

        if (document.getExtractedContent() == null || document.getExtractedContent().isBlank()) {
            throw new IllegalStateException("O documento ainda não teve o conteúdo extraído.");
        }

        document.setSummary(null);
        document.setStatus(DocumentStatus.PROCESSING);
        document.setUpdatedAt(LocalDateTime.now());
        documentRepository.save(document);

        KafkaSummaryRequestDTO requestDTO = KafkaSummaryRequestDTO.builder()
                .correlationId(correlationId)
                .documentId(documentId)
                .build();

        kafkaSummaryTemplate.send(KafkaTopicConfig.SUMMARY_REQUEST_TOPIC, correlationId, requestDTO);
    }
}
