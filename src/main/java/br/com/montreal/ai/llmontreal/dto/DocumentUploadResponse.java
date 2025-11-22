package br.com.montreal.ai.llmontreal.dto;

import br.com.montreal.ai.llmontreal.entity.Document;
import br.com.montreal.ai.llmontreal.entity.enums.DocumentStatus;

import java.time.LocalDateTime;
import java.util.List;

public record DocumentUploadResponse(
        long id,
        String fileType,
        String fileName,
        DocumentStatus status,
        LocalDateTime uploadedAt,
        String message,
        Integer totalDocuments,
        List<Long> documentIds
) {
    public DocumentUploadResponse(Document doc) {
        this(
                doc.getId(),
                doc.getFileType(),
                doc.getFileName(),
                doc.getStatus(),
                doc.getCreatedAt(),
                "Documento enviado com sucesso e aguardando processamento",
                1,
                List.of(doc.getId())
        );
    }

    public DocumentUploadResponse(Document firstDoc, int totalDocuments, List<Long> allDocumentIds) {
        this(
                firstDoc.getId(),
                firstDoc.getFileType(),
                firstDoc.getFileName(),
                firstDoc.getStatus(),
                firstDoc.getCreatedAt(),
                String.format("ZIP processado com sucesso: %d documentos criados e aguardando processamento", totalDocuments),
                totalDocuments,
                allDocumentIds
        );
    }
}
