package br.com.montreal.ai.llmontreal.exception.auth;

public class DuplicateUserException extends RuntimeException {
    public DuplicateUserException(String message) {
        super(message);
    }
}
