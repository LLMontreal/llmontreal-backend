package br.com.montreal.ai.llmontreal.service;

import br.com.montreal.ai.llmontreal.entity.Document;
import br.com.montreal.ai.llmontreal.entity.enums.DocumentStatus;
import br.com.montreal.ai.llmontreal.repository.DocumentRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class DocumentService {

    private final DocumentRepository documentRepository;

    public DocumentService(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    public Page<Document> getAllDocuments(Pageable pageable, DocumentStatus documentStatus) {
        if (documentStatus == null) {
            return documentRepository.findAll(pageable);
        }

        return documentRepository.findAllByStatus(pageable, documentStatus);
    }

}
