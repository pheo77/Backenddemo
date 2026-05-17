package com.example.execrise2.rag.model;

import java.util.List;

/**
 * [LAYER 2] DTO: Kết quả thô từ Dual Retrieval (trước khi fusion).
 * Bọc cả 2 luồng tìm kiếm để truyền vào RrfFusionService.
 */
public record HybridRetrievalResult(
    /** Top-20 chunk từ Vector Search (cosine similarity) */
    List<DocumentChunk> vectorChunks,
    /** Top-20 chunk từ BM25 Search (keyword matching) */
    List<DocumentChunk> bm25Chunks
) {}
