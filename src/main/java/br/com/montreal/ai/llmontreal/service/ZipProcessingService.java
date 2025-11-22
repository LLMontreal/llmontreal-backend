package br.com.montreal.ai.llmontreal.service;

import br.com.montreal.ai.llmontreal.entity.Document;
import br.com.montreal.ai.llmontreal.entity.enums.DocumentStatus;
import br.com.montreal.ai.llmontreal.exception.FileUploadException;
import br.com.montreal.ai.llmontreal.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class ZipProcessingService {

    private static final int BUFFER_SIZE = 8192;

    private final DocumentRepository documentRepository;
    private final DocumentExtractionService extractionService;
    private final Tika tika = new Tika();
    
    @Value("${file.upload.zip.max-entry-size:104857600}")
    private long maxEntrySize;

    public List<Long> processZipFile(byte[] zipData, String originalZipFileName, String correlationId) {
        List<Long> createdDocumentIds = new ArrayList<>();
        int[] stats = {0, 0, 0};

        try (ZipInputStream zis = new ZipInputStream(new java.io.ByteArrayInputStream(zipData))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                try {
                    processZipEntry(zis, entry, originalZipFileName, createdDocumentIds, stats, correlationId);
                } catch (Exception e) {
                    log.error("Error processing ZIP entry {}: {}", entry.getName(), e.getMessage());
                    stats[2]++;
                } finally {
                    zis.closeEntry();
                }
            }

            log.info("ZIP processing completed: {} - Processed={} Skipped={} Errors={}", 
                    originalZipFileName, stats[0], stats[1], stats[2]);

            return createdDocumentIds;

        } catch (Exception e) {
            log.error("Error reading ZIP file {}: {}", originalZipFileName, e.getMessage(), e);
            throw new FileUploadException("Erro ao processar arquivo ZIP: " + e.getMessage(), e);
        }
    }

    private void processZipEntry(ZipInputStream zis, ZipEntry entry, String zipFileName,
                                  List<Long> createdDocumentIds, int[] stats, String correlationId) throws Exception {
        String entryName = entry.getName();

        if (shouldSkipEntry(entry, entryName)) {
            stats[1]++;
            log.debug("Skipping entry: {}", entryName);
            return;
        }

        byte[] entryData = readZipEntryContent(zis, entry);
        if (entryData.length == 0) {
            stats[1]++;
            log.debug("Skipping empty entry: {}", entryName);
            return;
        }

        String contentType = detectContentType(entryData, entryName);
        
        if (!isSupportedContentType(contentType)) {
            stats[1]++;
            log.debug("Skipping unsupported type {} for entry: {}", contentType, entryName);
            return;
        }

        Document document = createDocumentFromZipEntry(entryName, zipFileName, contentType, entryData);
        Document savedDocument = documentRepository.save(document);
        
        log.info("Created document from ZIP: {} (ID: {}, Type: {})", 
                entryName, savedDocument.getId(), contentType);

        extractionService.extractContentAsync(savedDocument.getId(), correlationId);
        
        createdDocumentIds.add(savedDocument.getId());
        stats[0]++;
    }

    private boolean shouldSkipEntry(ZipEntry entry, String entryName) {
        if (entry.isDirectory()) {
            return true;
        }

        String normalized = entryName.toLowerCase();
        String baseName = entryName.substring(entryName.lastIndexOf('/') + 1);

        return normalized.contains("__macosx/")
                || normalized.startsWith("._")
                || normalized.equals("thumbs.db")
                || normalized.equals("desktop.ini")
                || baseName.startsWith(".");
    }

    private byte[] readZipEntryContent(ZipInputStream zis, ZipEntry entry) throws Exception {
        long size = entry.getSize();
        if (size > 0 && size > maxEntrySize) {
            log.warn("Skipping large file: {} ({}MB)", entry.getName(), size / (1024 * 1024));
            return new byte[0];
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[BUFFER_SIZE];
        int bytesRead;
        long total = 0;

        while ((bytesRead = zis.read(buffer)) != -1) {
            total += bytesRead;
            if (total > maxEntrySize) {
                log.warn("Truncating large file: {}", entry.getName());
                break;
            }
            out.write(buffer, 0, bytesRead);
        }

        return out.toByteArray();
    }

    private String detectContentType(byte[] data, String fileName) {
        try {
            return tika.detect(data, fileName);
        } catch (Exception e) {
            log.debug("Content type detection failed for {}", fileName);
            return "application/octet-stream";
        }
    }

    private boolean isSupportedContentType(String contentType) {
        List<String> supportedTypes = List.of(
                "application/pdf",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/msword",
                "text/plain",
                "image/jpeg",
                "image/png"
        );
        
        return supportedTypes.contains(contentType);
    }

    private Document createDocumentFromZipEntry(String entryName, String zipFileName, 
                                                 String contentType, byte[] data) {
        String fileName = extractFileName(zipFileName) + "/" + entryName;

        return Document.builder()
                .fileName(fileName)
                .fileType(contentType)
                .fileData(data)
                .status(DocumentStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private String extractFileName(String fullPath) {
        if (fullPath == null) {
            return "unknown";
        }
        int lastDot = fullPath.lastIndexOf('.');
        if (lastDot > 0) {
            return fullPath.substring(0, lastDot);
        }
        return fullPath;
    }
}

