package com.example.execrise2.rag.model;

import java.util.List;

/**
 * [LAYER 8] OUTPUT DTO: Câu trả lời hoàn chỉnh từ RAG pipeline.
 * Bao gồm: câu trả lời, danh sách nguồn trích dẫn, và metadata kỹ thuật.
 */
public record RagResponse(

    /** Câu trả lời do AI sinh ra (có kèm [Nguồn: X]) */
    String answer,

    /** Danh sách chunk nguồn đã được dùng để tạo câu trả lời */
    List<Citation> citations,

    /** Metadata kỹ thuật: latency, số chunk, provider... */
    RetrievalMeta meta

) {}
