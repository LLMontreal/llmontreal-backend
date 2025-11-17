package br.com.montreal.ai.llmontreal.controller;

import br.com.montreal.ai.llmontreal.dto.DocumentDTO;
import br.com.montreal.ai.llmontreal.dto.DocumentUploadResponse;
import br.com.montreal.ai.llmontreal.entity.enums.DocumentStatus;
import br.com.montreal.ai.llmontreal.service.DocumentService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.constraints.NotNull;

@RestController
@RequestMapping("/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping
    public ResponseEntity<Page<DocumentDTO>> getAllDocuments(
            Pageable pageable,
            @RequestParam(value = "status", required = false) DocumentStatus documentStatus) {
        Page<DocumentDTO> docsDTO = documentService.getAllDocuments(pageable, documentStatus)
                .map(DocumentDTO::new);
        return ResponseEntity.status(HttpStatus.OK).body(docsDTO);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentUploadResponse> uploadFile(@RequestParam("file") @NotNull MultipartFile file) {

        DocumentUploadResponse response = documentService.uploadFile(file);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping("/{id}/content")
    public ResponseEntity<String> getExtractedContent(@PathVariable Long id) {
        return documentService.getExtractedContent(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/summary")
    public ResponseEntity<String> getSummary(@PathVariable Long id) {
        return documentService.getSummary(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
