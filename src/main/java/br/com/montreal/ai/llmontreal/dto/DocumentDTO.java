package br.com.montreal.ai.llmontreal.dto;

import br.com.montreal.ai.llmontreal.entity.Document;
import br.com.montreal.ai.llmontreal.entity.enums.DocumentStatus;

import java.time.LocalDateTime;

public record DocumentDTO(
        Long id,
        DocumentStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String fileName,
        String fileType,
        String summary,
        String username
) {
    public DocumentDTO(Document doc) {
        this(
                doc.getId(),
                doc.getStatus(),
                doc.getCreatedAt(),
                doc.getUpdatedAt(),
                doc.getFileName(),
                doc.getFileType(),
                doc.getSummary(),
                doc.getUser().getUsername()
        );
    }
}