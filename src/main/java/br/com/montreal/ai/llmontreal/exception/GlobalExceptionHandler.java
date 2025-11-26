package br.com.montreal.ai.llmontreal.exception;

import br.com.montreal.ai.llmontreal.exception.auth.DuplicateUserException;
import br.com.montreal.ai.llmontreal.exception.auth.UnauthorizedAccessException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.util.HtmlUtils;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponseDTO> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest request) {
        String paramName = ex.getName();
        String invalidValue = ex.getValue() != null ? ex.getValue().toString() : "null";
        String errorMessage = String.format(
                "Invalid value '%s' for '%s' param",
                invalidValue, paramName
        );

        log.warn(errorMessage);

        ErrorResponseDTO errorDTO = new ErrorResponseDTO(
                HttpStatus.BAD_REQUEST.value(),
                "Bad Request",
                errorMessage,
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorDTO);
    }

    @ExceptionHandler(FileValidationException.class)
    public ResponseEntity<ErrorResponseDTO> handleFileValidation(
            FileValidationException ex,
            HttpServletRequest request) {
        log.warn("Erro de validação de arquivo: {}", ex.getMessage());

        String message = HtmlUtils.htmlEscape(ex.getMessage());
        String uri = HtmlUtils.htmlEscape(request.getRequestURI());

        ErrorResponseDTO errorDTO = new ErrorResponseDTO(
                HttpStatus.BAD_REQUEST.value(),
                "File Validation Error",
                message,
                uri
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorDTO);
    }

    @ExceptionHandler(FileUploadException.class)
    public ResponseEntity<ErrorResponseDTO> handleFileUpload(
            FileUploadException ex,
            HttpServletRequest request) {
        log.error("Erro ao fazer upload do arquivo: {}", ex.getMessage(), ex);

        String message = HtmlUtils.htmlEscape(ex.getMessage());
        String uri = HtmlUtils.htmlEscape(request.getRequestURI());

        ErrorResponseDTO errorDTO = new ErrorResponseDTO(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "File Upload Error",
                message,
                uri
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorDTO);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorResponseDTO> handleEntityNotFound(
            EntityNotFoundException e,
            HttpServletRequest req
    ) {
        log.error("Entidade não encontrada: {}", e.getMessage(), e);

        ErrorResponseDTO errorDTO = new ErrorResponseDTO(
                HttpStatus.NOT_FOUND.value(),
                "Entity not found",
                e.getMessage(),
                req.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorDTO);
    }

    @ExceptionHandler(OllamaException.class)
    public ResponseEntity<ErrorResponseDTO> handleOllamaException(
            OllamaException e,
            HttpServletRequest req
    ) {
        ErrorResponseDTO errorDTO = new ErrorResponseDTO(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Error getting Ollama Response",
                e.getMessage(),
                req.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorDTO);
    }

    @ExceptionHandler(TesseractException.class)
    public ResponseEntity<ErrorResponseDTO> handleTesseractException(
            TesseractException e,
            HttpServletRequest req
    ) {
        log.error("Tesseract OCR error: {}", e.getMessage(), e);

        String message = HtmlUtils.htmlEscape(e.getMessage());
        String uri = HtmlUtils.htmlEscape(req.getRequestURI());

        ErrorResponseDTO errorDTO = new ErrorResponseDTO(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Tesseract OCR Error",
                message,
                uri
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorDTO);
    }

    @ExceptionHandler(SummarizeException.class)
    public ResponseEntity<ErrorResponseDTO> handleOllamaException(
            SummarizeException e,
            HttpServletRequest req
    ) {

        String message = HtmlUtils.htmlEscape(e.getMessage());
        String uri = HtmlUtils.htmlEscape(req.getRequestURI());

        ErrorResponseDTO errorDTO = new ErrorResponseDTO(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Error generating summary",
                message,
                uri
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorDTO);
    }

    @ExceptionHandler(DuplicateUserException.class)
    public ResponseEntity<ErrorResponseDTO> handleDuplicateUser(
            DuplicateUserException ex,
            HttpServletRequest request) {
        log.warn("Duplicate user error: {}", ex.getMessage());

        ErrorResponseDTO errorDTO = new ErrorResponseDTO(
                HttpStatus.CONFLICT.value(),
                "Duplicate User",
                ex.getMessage(),
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorDTO);
    }

    @ExceptionHandler(UnauthorizedAccessException.class)
    public ResponseEntity<ErrorResponseDTO> handleUnauthorizedAccess(
            UnauthorizedAccessException ex,
            HttpServletRequest request) {
        log.warn("Unauthorized access attempt: {}", ex.getMessage());

        ErrorResponseDTO errorDTO = new ErrorResponseDTO(
                HttpStatus.FORBIDDEN.value(),
                "Forbidden",
                ex.getMessage(),
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorDTO);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponseDTO> handleBadCredentials(
            BadCredentialsException ex,
            HttpServletRequest request) {
        log.warn("Bad credentials: {}", ex.getMessage());

        ErrorResponseDTO errorDTO = new ErrorResponseDTO(
                HttpStatus.UNAUTHORIZED.value(),
                "Unauthorized",
                "Credenciais inválidas",
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorDTO);
    }

    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<ErrorResponseDTO> handleUserNotFound(
            UsernameNotFoundException ex,
            HttpServletRequest request) {
        log.warn("User not found: {}", ex.getMessage());

        ErrorResponseDTO errorDTO = new ErrorResponseDTO(
                HttpStatus.UNAUTHORIZED.value(),
                "Unauthorized",
                "Credenciais inválidas",
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorDTO);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponseDTO> handleAccessDenied(
            AccessDeniedException ex,
            HttpServletRequest request) {
        log.warn("Access denied: {}", ex.getMessage());

        ErrorResponseDTO errorDTO = new ErrorResponseDTO(
                HttpStatus.FORBIDDEN.value(),
                "Forbidden",
                "Acesso negado",
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorDTO);
    }
}