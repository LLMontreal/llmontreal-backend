package br.com.montreal.ai.llmontreal.service.extraction;

import br.com.montreal.ai.llmontreal.exception.ExtractionException;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@Slf4j
@Component
public class TesseractContentExtractor implements ContentExtractor {

    private static final List<String> SUPPORTED_IMAGE_TYPES = Arrays.asList(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/tiff",
            "image/tif",
            "image/bmp",
            "image/gif"
    );

    private final Tesseract tesseract;
    private Path tempTessdataDir;

    public TesseractContentExtractor(
            @Value("${tesseract.data-path:}") String dataPath,
            @Value("${tesseract.language:por+eng}") String language,
            @Value("${tesseract.page-segmentation-mode:3}") int pageSegMode,
            @Value("${tesseract.oem-mode:3}") int oemMode
    ) throws TesseractException {
        this.tesseract = new Tesseract();
        this.tempTessdataDir = null;
        
        try {
            String effectiveDataPath = setupTessdataPath(dataPath);
            tesseract.setDatapath(effectiveDataPath);
            log.info("Tesseract data path set to: {}", effectiveDataPath);
            
            log.info("Setting Tesseract language to: {}", language);
            tesseract.setLanguage(language);
            tesseract.setPageSegMode(pageSegMode);
            log.info("Setting Tesseract Page Segmentation Mode to: {}", pageSegMode);

            tesseract.setOcrEngineMode(oemMode);
            log.info("Setting Tesseract OCR Engine Mode to: {}", oemMode);
            
            if (tempTessdataDir != null) {
                Runtime.getRuntime().addShutdownHook(new Thread(this::cleanupTempTessdata));
            }
            
            log.info("TesseractContentExtractor initialized successfully");
        } catch (IOException e) {
            log.error("Failed to initialize TesseractContentExtractor", e);
            throw new TesseractException("Failed to initialize Tesseract", e);
        }
    }

    private String setupTessdataPath(String configuredDataPath) throws IOException {
        if (configuredDataPath != null && !configuredDataPath.isEmpty()) {
            File dataPathFile = new File(configuredDataPath);
            if (dataPathFile.exists() && dataPathFile.isDirectory()) {
                log.info("Using configured tessdata path: {}", configuredDataPath);
                tempTessdataDir = null;
                return configuredDataPath;
            } else {
                log.warn("Configured tessdata path does not exist: {}", configuredDataPath);
            }
        }

        try {
            log.info("Attempting to load tessdata from classpath (resources/tessdata/)...");
            Path tempDir = extractTessdataFromClasspath();
            if (tempDir != null) {
                tempTessdataDir = tempDir;
                log.info("Successfully extracted tessdata to temporary directory: {}", tempDir);
                return tempDir.toString();
            }
        } catch (Exception e) {
            log.warn("Failed to load tessdata from classpath: {}", e.getMessage());
        }

        log.warn("No tessdata found in classpath. Attempting to use system installation...");
        log.warn("Please ensure Tesseract is installed or add traineddata files to src/main/resources/tessdata/");
        tempTessdataDir = null;
        return "";
    }

    private Path extractTessdataFromClasspath() throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath:tessdata/*.traineddata");

        if (resources.length == 0) {
            log.debug("No traineddata files found in classpath");
            return null;
        }

        Path tempDir = Files.createTempDirectory("tesseract-tessdata-");
        log.debug("Created temporary directory for tessdata: {}", tempDir);

        for (Resource resource : resources) {
            String filename = resource.getFilename();
            if (filename == null) continue;

            File destFile = tempDir.resolve(filename).toFile();
            
            try (InputStream inputStream = resource.getInputStream();
                 FileOutputStream outputStream = new FileOutputStream(destFile)) {
                
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                
                log.debug("Extracted tessdata file: {}", filename);
            }
        }

        log.info("Extracted {} traineddata file(s) from classpath", resources.length);
        return tempDir;
    }

    @Override
    public String extractContent(InputStream inputStream, String contentType) throws ExtractionException {
        if (!supportsThisContentType(contentType)) {
            throw new ExtractionException("Content type " + contentType + " not supported by Tesseract");
        }

        try {
            log.info("Extracting content using Tesseract OCR for content type: {}", contentType);
            long startTime = System.currentTimeMillis();

            BufferedImage image = ImageIO.read(inputStream);
            
            if (image == null) {
                String message = "Failed to read image from stream. The image might be corrupted or in an unsupported format.";
                log.error(message);
                throw new ExtractionException(message);
            }

            log.debug("Image loaded successfully. Dimensions: {}x{}", image.getWidth(), image.getHeight());

            String extractedText = tesseract.doOCR(image);
            
            long duration = System.currentTimeMillis() - startTime;

            if (extractedText == null || extractedText.trim().isEmpty()) {
                log.warn("Tesseract OCR returned empty content. This may indicate:");
                log.warn("  - Image contains no text");
                log.warn("  - Image quality is too low");
                log.warn("  - Text is in an unsupported language");
                log.warn("  - Image preprocessing might be needed");
                return "";
            }

            String cleanedContent = cleanExtractedText(extractedText);
            log.info("Successfully extracted {} characters (raw: {}) using Tesseract OCR in {}ms", 
                    cleanedContent.length(), extractedText.length(), duration);
            
            return cleanedContent;

        } catch (TesseractException e) {
            String message = "Tesseract OCR processing error: " + e.getMessage();
            log.error(message, e);
            throw new ExtractionException(message, e);
        } catch (IOException e) {
            String message = "Error reading image stream: " + e.getMessage();
            log.error(message, e);
            throw new ExtractionException(message, e);
        } catch (Exception e) {
            String message = "Unexpected error during OCR extraction: " + e.getMessage();
            log.error(message, e);
            throw new ExtractionException(message, e);
        }
    }

    @Override
    public boolean supportsThisContentType(String contentType) {
        return contentType != null && SUPPORTED_IMAGE_TYPES.stream()
                .anyMatch(type -> contentType.toLowerCase().startsWith(type));
    }

    @Override
    public int getPriority() {
        return 5;
    }

    private String cleanExtractedText(String text) {
        if (text == null) {
            return "";
        }

        return text
                .replaceAll("[\\p{Cntrl}&&[^\n\r\t]]", "")
                .replaceAll("[ \\t]+", " ")
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .replaceAll("\n{3,}", "\n\n")
                .lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .reduce((a, b) -> a + "\n" + b)
                .orElse("")
                .trim();
    }

    private void cleanupTempTessdata() {
        if (tempTessdataDir != null && Files.exists(tempTessdataDir)) {
            try (Stream<Path> paths = Files.walk(tempTessdataDir)) {
                paths.sorted(Comparator.reverseOrder())
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                            } catch (IOException e) {
                                log.warn("Failed to delete temporary file: {}", path, e);
                            }
                        });

                log.debug("Cleaned up temporary tessdata directory: {}", tempTessdataDir);

            } catch (IOException e) {
                log.warn("Failed to cleanup temporary tessdata directory: {}", tempTessdataDir, e);
            }
        }
    }
}

