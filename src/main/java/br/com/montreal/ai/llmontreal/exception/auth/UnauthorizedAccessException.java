package br.com.montreal.ai.llmontreal.exception.auth;

public class UnauthorizedAccessException extends RuntimeException {
    public UnauthorizedAccessException(String message) {
        super(message);
    }
}
