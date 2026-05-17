package com.example.execrise2.rag.model;

/**
 * [LAYER 8] DTO: Metadata kỹ thuật về quá trình xử lý RAG.
 * Dùng để debug, monitor, và hiển thị thông tin cho developer.
 */
public record RetrievalMeta(
    /** Tổng thời gian xử lý toàn pipeline (ms) */
    long totalLatencyMs,
    /** Thời gian riêng của bước tìm kiếm kép (ms) */
    long retrievalLatencyMs,
    /** Số chunk từ Vector Search */
    int vectorChunkCount,
    /** Số chunk từ BM25 Search */
    int bm25ChunkCount,
    /** Số chunk sau khi fuse + re-rank (đưa vào prompt) */
    int finalChunkCount,
    /** Tên AI provider đã được dùng để sinh câu trả lời */
    String providerUsed,
    /** Câu hỏi đã được biến đổi (sau HyDE nếu bật) */
    String transformedQuery,
    /** Hallucination check: true = không bịa, false = đáng ngờ */
    boolean isGrounded
) {}
