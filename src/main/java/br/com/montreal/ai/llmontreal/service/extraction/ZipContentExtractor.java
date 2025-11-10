package br.com.montreal.ai.llmontreal.service.extraction;

import br.com.montreal.ai.llmontreal.exception.ExtractionException;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Slf4j
@Component
public class ZipContentExtractor implements ContentExtractor {

    private static final String ZIP_CONTENT_TYPE = "application/zip";
    private static final int MAX_FILE_SIZE = 100 * 1024 * 1024;
    private static final int BUFFER_SIZE = 8192;
    
    private final List<ContentExtractor> extractors;
    private final Tika tika;

    public ZipContentExtractor(List<ContentExtractor> extractors) {
        this.extractors = extractors;
        this.tika = new Tika();
        log.info("ZipContentExtractor initialized successfully");
    }

    @Override
    public String extractContent(InputStream inputStream, String contentType) throws ExtractionException {
        if (!supportsThisContentType(contentType)) {
            throw new ExtractionException("Content type " + contentType + " not supported by ZipContentExtractor");
        }

        log.info("Starting ZIP file extraction");
        long startTime = System.currentTimeMillis();
        
        List<String> extractedContents = new ArrayList<>();
        int processedFiles = 0;
        int skippedFiles = 0;
        int errorFiles = 0;

        try (ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
            ZipEntry entry;
            
            while ((entry = zipInputStream.getNextEntry()) != null) {
                String entryName = entry.getName();
                
                try {
                    if (entry.isDirectory()) {
                        log.debug("Skipping directory: {}", entryName);
                        continue;
                    }

                    if (isSystemFile(entryName)) {
                        log.debug("Skipping system file: {}", entryName);
                        skippedFiles++;
                        continue;
                    }

                    log.info("Processing ZIP entry: {}", entryName);
                    
                    byte[] fileContent = readZipEntryContent(zipInputStream, entry);
                    
                    if (fileContent.length == 0) {
                        log.warn("Empty file in ZIP: {}", entryName);
                        skippedFiles++;
                        continue;
                    }

                    // Detect content type
                    String detectedContentType = detectContentType(fileContent, entryName);
                    log.info("Detected content type for {}: {}", entryName, detectedContentType);

                    Optional<ContentExtractor> extractor = findExtractor(detectedContentType);
                    
                    if (extractor.isEmpty()) {
                        log.warn("No extractor found for file: {} (type: {})", entryName, detectedContentType);
                        skippedFiles++;
                        continue;
                    }

                    try (ByteArrayInputStream fileInputStream = new ByteArrayInputStream(fileContent)) {
                        String content = extractor.get().extractContent(fileInputStream, detectedContentType);
                        
                        if (content != null && !content.trim().isEmpty()) {
                            extractedContents.add(formatExtractedContent(entryName, content));
                            processedFiles++;
                            log.info("Successfully extracted content from: {} ({} characters)", 
                                    entryName, content.length());
                        } else {
                            log.warn("Empty content extracted from: {}", entryName);
                            skippedFiles++;
                        }
                    }
                    
                } catch (Exception e) {
                    log.error("Error processing ZIP entry: {}", entryName, e);
                    errorFiles++;
                } finally {
                    zipInputStream.closeEntry();
                }
            }
            
            long duration = System.currentTimeMillis() - startTime;
            log.info("ZIP extraction completed in {}ms. Processed: {}, Skipped: {}, Errors: {}", 
                    duration, processedFiles, skippedFiles, errorFiles);
            
            if (extractedContents.isEmpty()) {
                log.warn("No content could be extracted from ZIP file");
                return "";
            }
            
            return String.join("\n\n" + "=".repeat(80) + "\n\n", extractedContents);
            
        } catch (IOException e) {
            String message = "Error reading ZIP file: " + e.getMessage();
            log.error(message, e);
            throw new ExtractionException(message, e);
        } catch (Exception e) {
            String message = "Unexpected error during ZIP extraction: " + e.getMessage();
            log.error(message, e);
            throw new ExtractionException(message, e);
        }
    }

    @Override
    public boolean supportsThisContentType(String contentType) {
        return contentType != null && ZIP_CONTENT_TYPE.equalsIgnoreCase(contentType);
    }

    @Override
    public int getPriority() {
        return 20;
    }

    private byte[] readZipEntryContent(ZipInputStream zipInputStream, ZipEntry entry) throws IOException {
        long size = entry.getSize();
        
        if (size > MAX_FILE_SIZE) {
            log.warn("File {} exceeds maximum size limit ({}MB), skipping", 
                    entry.getName(), MAX_FILE_SIZE / (1024 * 1024));
            return new byte[0];
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[BUFFER_SIZE];
        int bytesRead;
        long totalBytesRead = 0;

        while ((bytesRead = zipInputStream.read(buffer)) != -1) {
            totalBytesRead += bytesRead;
            
            if (totalBytesRead > MAX_FILE_SIZE) {
                log.warn("File {} exceeded maximum size limit while reading, truncating", entry.getName());
                break;
            }
            
            outputStream.write(buffer, 0, bytesRead);
        }

        return outputStream.toByteArray();
    }

    private String detectContentType(byte[] fileContent, String fileName) {
        try {
            return tika.detect(fileContent, fileName);
        } catch (Exception e) {
            log.warn("Failed to detect content type for {}, using default", fileName, e);
            return "application/octet-stream";
        }
    }

    private Optional<ContentExtractor> findExtractor(String contentType) {
        return extractors.stream()
                .filter(ex -> !(ex instanceof ZipContentExtractor)) // Exclude self
                .filter(ex -> ex.supportsThisContentType(contentType))
                .findFirst();
    }

    private boolean isSystemFile(String fileName) {
        String normalizedName = fileName.toLowerCase();
        
        if (normalizedName.contains("__macosx/") || normalizedName.startsWith("._")) {
            return true;
        }
        
        if (normalizedName.equals("thumbs.db") || normalizedName.equals("desktop.ini")) {
            return true;
        }
        
        String[] parts = fileName.split("/");
        String name = parts[parts.length - 1];
        if (name.startsWith(".")) {
            return true;
        }
        
        return false;
    }

    private String formatExtractedContent(String fileName, String content) {
        StringBuilder formatted = new StringBuilder();
        formatted.append("FILE: ").append(fileName).append("\n");
        formatted.append("-".repeat(80)).append("\n");
        formatted.append(content.trim());
        return formatted.toString();
    }
}

