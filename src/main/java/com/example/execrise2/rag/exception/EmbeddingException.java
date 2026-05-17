package com.example.execrise2.rag.exception;

/**
 * Exception: Dịch vụ Embedding API bị lỗi hoặc không phản hồi.
 */
public class EmbeddingException extends RuntimeException {
    public EmbeddingException(String message) {
        super(message);
    }

    public EmbeddingException(String message, Throwable cause) {
        super(message, cause);
    }
}
