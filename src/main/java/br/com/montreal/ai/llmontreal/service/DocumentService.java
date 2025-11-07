package br.com.montreal.ai.llmontreal.service;

import br.com.montreal.ai.llmontreal.dto.DocumentUploadResponse;
import br.com.montreal.ai.llmontreal.entity.Document;
import br.com.montreal.ai.llmontreal.entity.enums.DocumentStatus;
import br.com.montreal.ai.llmontreal.exception.FileUploadException;
import br.com.montreal.ai.llmontreal.exception.FileValidationException;
import br.com.montreal.ai.llmontreal.repository.DocumentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
public class DocumentService {

    private final DocumentRepository documentRepository;
    private static final long MAX_FILE_SIZE = 25L * 1024 * 1024;
    private static final List<String> ALLOWED_CONTENT_TYPES = Arrays.asList(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "image/jpeg",
            "image/png",
            "application/zip",
            "text/plain"
    );

    public DocumentService(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

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

    public DocumentUploadResponse uploadFile(MultipartFile file) {
        validateFile(file);

        try {
            byte[] fileData = file.getBytes();

            Document document = Document.builder()
                    .fileName(file.getOriginalFilename())
                    .fileType(file.getContentType())
                    .fileData(fileData)
                    .status(DocumentStatus.PENDING)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            Document savedDocument = documentRepository.save(document);
            log.info("Arquivo carregado com sucesso: {} (ID: {})", 
                    savedDocument.getFileName(), savedDocument.getId());
            
            return new DocumentUploadResponse(savedDocument);
            
        } catch (IOException e) {
            String errorMessage = String.format(
                    "Erro ao ler o conteúdo do arquivo: %s", file.getOriginalFilename());
            log.error(errorMessage, e);
            throw new FileUploadException(errorMessage, e);
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
}
