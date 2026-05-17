package com.example.execrise2.rag.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

/**
 * [LAYER 8] GLOBAL EXCEPTION HANDLER — Chuẩn RFC 7807 ProblemDetail.
 *
 * Bắt tất cả lỗi trong RAG module và trả về JSON chuẩn:
 * {
 *   "type": "...",
 *   "title": "...",
 *   "status": 422,
 *   "detail": "...",
 *   "timestamp": "...",
 *   "suggestion": "..."
 * }
 */
@RestControllerAdvice
public class RagExceptionHandler {

    /**
     * Không tìm thấy context → 422 Unprocessable Entity
     */
    @ExceptionHandler(NoContextFoundException.class)
    public ProblemDetail handleNoContext(NoContextFoundException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
            HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        pd.setTitle("Không tìm thấy ngữ cảnh phù hợp");
        pd.setProperty("timestamp", Instant.now());
        pd.setProperty("suggestion", "Thử diễn đạt câu hỏi theo cách khác, hoặc kiểm tra lại docType và language.");
        return pd;
    }

    /**
     * Embedding API lỗi → 503 Service Unavailable
     */
    @ExceptionHandler(EmbeddingException.class)
    public ProblemDetail handleEmbeddingError(EmbeddingException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
            HttpStatus.SERVICE_UNAVAILABLE, "Dịch vụ Embedding tạm thời không khả dụng.");
        pd.setTitle("Embedding Service Error");
        pd.setProperty("timestamp", Instant.now());
        pd.setProperty("retry_after", "30s");
        pd.setProperty("detail", ex.getMessage());
        return pd;
    }

    /**
     * Validation lỗi (@Valid) → 400 Bad Request
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .reduce("", (a, b) -> a + (a.isEmpty() ? "" : "; ") + b);

        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST, detail);
        pd.setTitle("Dữ liệu đầu vào không hợp lệ");
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    /**
     * Lỗi không xác định → 500 Internal Server Error
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneral(Exception ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR, "Đã xảy ra lỗi nội bộ. Vui lòng thử lại.");
        pd.setTitle("Internal Server Error");
        pd.setProperty("timestamp", Instant.now());
        pd.setProperty("error_type", ex.getClass().getSimpleName());
        return pd;
    }
}
