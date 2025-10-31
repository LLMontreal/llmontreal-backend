package br.com.montreal.ai.llmontreal.dto;

import br.com.montreal.ai.llmontreal.entity.Document;
import br.com.montreal.ai.llmontreal.entity.enums.DocumentStatus;

import java.time.LocalDateTime;

public record DocumentUploadResponse(
        long id,
        String fileType,
        String fileName,
        DocumentStatus status,
        LocalDateTime uploadedAt,
        String message
) {
    public DocumentUploadResponse(Document doc) {
        this(
                doc.getId(),
                doc.getFileType(),
                doc.getFileName(),
                doc.getStatus(),
                doc.getCreatedAt(),
                "Documento enviado com sucesso e aguardando processamento"
        );
    }
}
