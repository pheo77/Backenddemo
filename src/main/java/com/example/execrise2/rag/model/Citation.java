package com.example.execrise2.rag.model;

import java.util.UUID;

/**
 * [LAYER 7] DTO: Một nguồn trích dẫn đơn lẻ.
 * Mỗi Citation tương ứng với một DocumentChunk đã được dùng để tạo câu trả lời.
 */
public record Citation(
    UUID chunkId,
    String sourceUrl,
    String docType,
    String language,
    /** Đoạn trích ngắn (~200 ký tự) từ chunk để hiển thị bằng chứng */
    String snippet,
    /** Điểm RRF tổng hợp — càng cao càng liên quan */
    double relevanceScore
) {}
