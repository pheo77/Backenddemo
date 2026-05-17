package com.example.execrise2.rag.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * [LAYER 2-3] CẤU HÌNH TÌM KIẾM
 * Cho phép caller điều chỉnh hành vi của Hybrid Retriever.
 */
public record RetrievalOptions(

    /** Loại tài liệu cần tìm: grammar, vocabulary, kanji, culture... */
    @NotBlank(message = "docType là bắt buộc")
    String docType,

    /** Ngôn ngữ: vi, ja, en */
    @NotBlank(message = "language là bắt buộc")
    String language,

    /** Số chunk tối đa trả về sau fusion */
    @Min(1) @Max(50)
    int topK,

    /** Danh sách permission levels user hiện tại được phép xem */
    List<String> allowedPermissions,

    /**
     * Bật/tắt HyDE (Hypothetical Document Embedding).
     * Mặc định: false (tìm bằng câu hỏi gốc).
     */
    boolean useHyDE,

    /**
     * Bật/tắt Multi-Query expansion.
     * Mặc định: false.
     */
    boolean useMultiQuery

) {
    /** Constructor với giá trị mặc định hợp lý */
    public static RetrievalOptions defaults(String docType, String language) {
        return new RetrievalOptions(
            docType,
            language,
            10,
            List.of("PUBLIC"),
            false,
            false
        );
    }
}
