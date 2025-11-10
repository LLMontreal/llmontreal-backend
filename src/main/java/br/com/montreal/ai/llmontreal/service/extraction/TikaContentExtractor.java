package br.com.montreal.ai.llmontreal.service.extraction;

import br.com.montreal.ai.llmontreal.exception.ExtractionException;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
public class TikaContentExtractor implements ContentExtractor {

    private static final List<String> SUPPORTED_TYPES = Arrays.asList(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "text/plain",
            "application/zip"
    );

    private final Parser parser;

    public TikaContentExtractor() {
        this.parser = new AutoDetectParser();
    }

    @Override
    public String extractContent(InputStream inputStream, String contentType) throws ExtractionException {

        if(!supportsThisContentType(contentType)) {
            throw new ExtractionException("Content type " + contentType + " not supported");
        }

        try {
            log.info("Extracting content using Apache Tika for content type: {}", contentType);

            StringWriter writer = new StringWriter();
            BodyContentHandler handler = new BodyContentHandler(writer);
            
            Metadata metadata = new Metadata();
            metadata.set(Metadata.CONTENT_TYPE, contentType);
            ParseContext context = new ParseContext();
            context.set(Parser.class, parser);

            log.debug("Starting Tika parsing with unlimited content length...");
            parser.parse(inputStream, handler, metadata, context);

            String extractedContent = writer.toString();
            log.debug("Raw extraction completed. Length before cleaning: {}", extractedContent != null ? extractedContent.length() : 0);

            if(extractedContent == null || extractedContent.trim().isEmpty()) {
                log.warn("Extracted content is empty. This may indicate:");
                log.warn("  - PDF is image-based (needs OCR)");
                log.warn("  - PDF is encrypted/protected");
                log.warn("  - File is corrupted");
                return "";
            }

            String cleanedContent = cleanExtractedText(extractedContent);
            log.info("Successfully extracted {} characters (raw: {}) using Tika", cleanedContent.length(), extractedContent.length());
            return cleanedContent;
        } catch (IOException e) {
            String message = "Error reading document stream: " + e.getMessage();
            log.error(message, e);
            throw new ExtractionException(message, e);
        } catch (SAXException e) {
            String message = "Error parsing document content: " + e.getMessage();
            log.error(message, e);
            throw new ExtractionException(message, e);
        } catch (TikaException e) {
            String message = "Tika processing error: " + e.getMessage();
            log.error(message, e);
            throw new ExtractionException(message, e);
        } catch (Exception e) {
            String message = "Unexpected error during extraction: " + e.getMessage();
            log.error(message, e);
            throw new ExtractionException(message, e);
        }
    }

    @Override
    public boolean supportsThisContentType(String contentType) {
        return contentType != null && SUPPORTED_TYPES.contains(contentType.toLowerCase());
    }

    @Override
    public int getPriority() {
        return 10;
    }

    private String cleanExtractedText(String text) {
        if(text == null) {
            return "";
        }

        return text
                .replaceAll("[\\p{Cntrl}&&[^\n\r\t]]", "")
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\r\n", "\n")
                .replaceAll("\r", "\n")
                .replaceAll("\n{3,}", "\n\n")
                .trim();
    }
}
