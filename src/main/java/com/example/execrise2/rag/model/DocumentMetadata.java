package com.example.execrise2.rag.model;

/**
 * [LAYER 1] DTO: Metadata gắn kèm khi upload tài liệu mới.
 * Dùng trong DocumentIngestionPipeline để đánh nhãn chunk.
 */
public record DocumentMetadata(
    String sourceUrl,
    String docType,
    String language,
    String version,
    String permissionLevel
) {
    /** Constructor rút gọn cho trường hợp đơn giản */
    public DocumentMetadata(String docType, String language) {
        this("unknown", docType, language, "1.0", "PUBLIC");
    }
}
