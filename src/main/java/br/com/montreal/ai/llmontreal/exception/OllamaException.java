package br.com.montreal.ai.llmontreal.exception;

public class OllamaException extends RuntimeException {
    public OllamaException(String message, Throwable cause) {
        super(message, cause);
    }
}
