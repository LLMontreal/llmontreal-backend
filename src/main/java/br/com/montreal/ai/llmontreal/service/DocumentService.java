package br.com.montreal.ai.llmontreal.service;

import br.com.montreal.ai.llmontreal.dto.DocumentUploadResponse;
import br.com.montreal.ai.llmontreal.entity.Document;
import br.com.montreal.ai.llmontreal.entity.enums.DocumentStatus;
import br.com.montreal.ai.llmontreal.exception.FileUploadException;
import br.com.montreal.ai.llmontreal.exception.FileValidationException;
import br.com.montreal.ai.llmontreal.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentExtractionService extractionService;
    private final ZipProcessingService zipProcessingService;

    private static final long MAX_FILE_SIZE = 25L * 1024 * 1024;
    private static final String ZIP_CONTENT_TYPE = "application/zip";
    private static final List<String> ALLOWED_CONTENT_TYPES = Arrays.asList(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "image/jpeg",
            "image/png",
            ZIP_CONTENT_TYPE,
            "text/plain"
    );

    public Page<Document> getAllDocuments(Pageable pageable, DocumentStatus documentStatus) {
        if (documentStatus == null) {
            return documentRepository.findAll(pageable);
        }

        return documentRepository.findAllByStatus(pageable, documentStatus);
    }

    public Page<Document> getAllDocuments(Pageable pageable) {
        return documentRepository.findAll(pageable);
    }

    public Page<Document> getAllDocumentsByStatus(Pageable pageable, DocumentStatus status) {
        return documentRepository.findAllByStatus(pageable, status);
    }

    public DocumentUploadResponse uploadFile(MultipartFile file, String correlationId) {
        validateFile(file);

        try {
            byte[] fileData = file.getBytes();
            String contentType = file.getContentType();
            String fileName = file.getOriginalFilename();

            if (ZIP_CONTENT_TYPE.equalsIgnoreCase(contentType)) {
                return processZipFile(fileData, fileName);
            }

            return processSingleFile(fileName, contentType, fileData, correlationId);

        } catch (IOException e) {
            String errorMessage = String.format(
                    "Erro ao ler o conteúdo do arquivo: %s", file.getOriginalFilename());
            log.error(errorMessage, e);
            throw new FileUploadException(errorMessage, e);
        }
    }

    private DocumentUploadResponse processZipFile(byte[] zipData, String fileName) {
        log.info("Processing ZIP file: {}", fileName);

        List<Long> documentIds = zipProcessingService.processZipFile(zipData, fileName);

        if (documentIds.isEmpty()) {
            log.warn("No valid documents found in ZIP: {}", fileName);
            throw new FileUploadException("Nenhum arquivo válido encontrado no ZIP");
        }

        log.info("ZIP processed successfully: {} - Created {} documents", fileName, documentIds.size());

        Document firstDocument = documentRepository.findById(documentIds.get(0))
                .orElseThrow(() -> new FileUploadException("Documento criado não encontrado"));

        return new DocumentUploadResponse(firstDocument, documentIds.size(), documentIds);
    }

    private DocumentUploadResponse processSingleFile(String fileName, String contentType, byte[] fileData, String correlationId) {
        Document document = Document.builder()
                .fileName(fileName)
                .fileType(contentType)
                .fileData(fileData)
                .status(DocumentStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        Document savedDocument = documentRepository.save(document);
        log.info("Arquivo carregado com sucesso: {} (ID: {})",
                savedDocument.getFileName(), savedDocument.getId());

        extractionService.extractContentAsync(savedDocument.getId(), correlationId);
        log.info("Extração assíncrona iniciada para documento ID: {}", savedDocument.getId());

        return new DocumentUploadResponse(savedDocument);
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
                            MAX_FILE_SIZE / (1024 * 1024))
            );
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new FileValidationException(
                    String.format("Tipo de arquivo não suportado: %s. Tipos aceitos: %s",
                            contentType, String.join(", ", ALLOWED_CONTENT_TYPES))
            );
        }

        log.debug("Arquivo validado: {} - {} - {} bytes",
                fileName, contentType, file.getSize());
    }

    public Optional<String> getExtractedContent(Long documentId) {
        return documentRepository.findById(documentId)
                .map(Document::getExtractedContent);
    }

    public Optional<String> getSummary(Long documentId) {
        return documentRepository.findById(documentId)
                .map(Document::getSummary);
    }
}
