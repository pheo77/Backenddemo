package com.example.execrise2.rag.exception;

/**
 * Exception: Không tìm được chunk nào phù hợp với câu hỏi.
 * Được ném khi Vector + BM25 đều trả về danh sách rỗng.
 */
public class NoContextFoundException extends RuntimeException {
    public NoContextFoundException(String question) {
        super("Không tìm thấy ngữ cảnh phù hợp cho câu hỏi: \"" + question + "\". " +
              "Hãy thử diễn đạt câu hỏi theo cách khác hoặc kiểm tra docType/language.");
    }
}
