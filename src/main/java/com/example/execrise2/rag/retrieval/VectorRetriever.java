package com.example.execrise2.rag.retrieval;

import com.example.execrise2.rag.ingestion.EmbeddingClientFacade;
import com.example.execrise2.rag.model.DocumentChunk;
import com.example.execrise2.rag.model.RetrievalOptions;
import com.example.execrise2.rag.repository.DocumentChunkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * [LAYER 2] VECTOR RETRIEVER — Tìm kiếm Dense bằng Cosine Similarity (PGVector).
 *
 * Ưu điểm: Hiểu ngữ nghĩa, bắt được đồng nghĩa và paraphrase.
 * Nhược điểm: Bỏ sót keyword chính xác (tên riêng, mã code, số liệu).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VectorRetriever {

    private final DocumentChunkRepository repository;
    private final EmbeddingClientFacade embeddingClient;

    private static final int DEFAULT_TOP_K = 20;

    /**
     * Tìm Top-K chunk gần nhất về mặt ngữ nghĩa.
     *
     * @param queryText Câu hỏi hoặc hypothetical document (sau HyDE)
     * @param options   Cấu hình lọc (docType, language, permissions...)
     * @return Danh sách chunk xếp theo mức độ liên quan giảm dần
     */
    public List<DocumentChunk> search(String queryText, RetrievalOptions options) {
        log.debug("🔍 [VECTOR] Tìm kiếm: '{}...' (docType={}, lang={})",
            queryText.substring(0, Math.min(50, queryText.length())),
            options.docType(), options.language());

        // Bước 1: Chuyển câu hỏi → vector
        float[] queryVector = embeddingClient.embed(queryText);

        // Bước 2: Tìm kiếm trong DB với permission filter
        List<String> allowedLevels = options.allowedPermissions() != null
            ? options.allowedPermissions()
            : List.of("PUBLIC");

        List<DocumentChunk> results = repository.findSimilarWithPermission(
            queryVector,
            options.docType(),
            options.language(),
            allowedLevels,
            DEFAULT_TOP_K
        );

        log.debug("   → Vector search trả về {} chunk", results.size());
        return results;
    }

    /**
     * Overload đơn giản với vector đã tính sẵn (tránh embed lại khi dùng chung).
     */
    public List<DocumentChunk> search(float[] queryVector, RetrievalOptions options) {
        List<String> allowedLevels = options.allowedPermissions() != null
            ? options.allowedPermissions()
            : List.of("PUBLIC");

        return repository.findSimilarWithPermission(
            queryVector, options.docType(), options.language(), allowedLevels, DEFAULT_TOP_K
        );
    }
}
