package br.com.montreal.ai.llmontreal.controller;

import br.com.montreal.ai.llmontreal.dto.DocumentDTO;
import br.com.montreal.ai.llmontreal.entity.Document;
import br.com.montreal.ai.llmontreal.entity.enums.DocumentStatus;
import br.com.montreal.ai.llmontreal.service.DocumentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/documents")
public class DocumentController {

    @Autowired
    private DocumentService documentService;

    @GetMapping
    public ResponseEntity<Page<DocumentDTO>> getAllDocuments(
            Pageable pageable,
            @RequestParam(value = "status", required = false) DocumentStatus documentStatus) {
        Page<Document> docs;

        if (documentStatus != null) {
            docs = documentService.getAllDocumentsByStatus(pageable, documentStatus);
        } else {
            docs = documentService.getAllDocuments(pageable);
        }

        Page<DocumentDTO> docsDTO = docs.map(DocumentDTO::toDTO);
        return ResponseEntity.status(HttpStatus.OK).body(docsDTO);
    }


}
