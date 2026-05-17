package com.example.execrise2.rag.retrieval;

import com.example.execrise2.rag.model.DocumentChunk;
import com.example.execrise2.rag.repository.DocumentChunkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * [LAYER 3] BM25 RETRIEVER — Tìm kiếm Sparse bằng Keyword Matching.
 *
 * Ưu điểm: Bắt chính xác từ khóa đặc thù (tên riêng, mã code, ngữ pháp cụ thể).
 * Nhược điểm: Không hiểu ngữ nghĩa, bỏ sót đồng nghĩa.
 *
 * Implementation hiện tại: LIKE query (JPQL) — Fallback đơn giản.
 *
 * TODO: Tích hợp Hibernate Search + Lucene để có BM25 thật sự:
 * <pre>
 *   // 1. Thêm @FullTextField vào DocumentChunk.content
 *   // 2. Dùng SearchSession (Hibernate Search) để tìm kiếm BM25 chuẩn:
 *   return searchSession.search(DocumentChunk.class)
 *       .where(f -> f.bool()
 *           .must(f.match().field("content").matching(query).boost(2.0f))
 *           .filter(f.match().field("docType").matching(docType))
 *       )
 *       .sort(f -> f.score())
 *       .fetchHits(k);
 * </pre>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BM25Retriever {

    private final DocumentChunkRepository repository;
    private static final int DEFAULT_TOP_K = 20;

    /**
     * Tìm kiếm keyword và trả về Top-K chunk phù hợp nhất.
     *
     * @param query   Câu hỏi gốc (chưa biến đổi)
     * @param docType Loại tài liệu để thu hẹp phạm vi tìm kiếm
     * @param topK    Số lượng kết quả tối đa
     * @return Danh sách chunk chứa keyword
     */
    public List<DocumentChunk> search(String query, String docType, int topK) {
        log.debug("🔍 [BM25] Keyword search: '{}' (docType={})", 
            query.substring(0, Math.min(50, query.length())), docType);

        // Trích xuất keyword quan trọng từ câu hỏi
        String keyword = extractPrimaryKeyword(query);

        List<DocumentChunk> results = repository.findByKeyword(keyword, docType);

        // Giới hạn số lượng kết quả
        List<DocumentChunk> limited = results.size() > topK
            ? results.subList(0, topK)
            : results;

        log.debug("   → BM25 search trả về {} chunk (keyword='{}')", limited.size(), keyword);
        return limited;
    }

    /**
     * Overload với topK mặc định.
     */
    public List<DocumentChunk> search(String query, String docType) {
        return search(query, docType, DEFAULT_TOP_K);
    }

    // ──────────────────────────────────────────────────────────────
    //  KEYWORD EXTRACTION (Heuristic đơn giản)
    // ──────────────────────────────────────────────────────────────

    /**
     * Trích xuất keyword chính từ câu hỏi bằng heuristic đơn giản.
     * Loại bỏ stop words tiếng Việt và giữ lại từ có nghĩa nhất.
     *
     * TODO: Thay bằng NLP tokenizer (JMdicts cho tiếng Nhật, VnCoreNLP cho tiếng Việt)
     */
    private String extractPrimaryKeyword(String query) {
        // Stop words tiếng Việt phổ biến
        String[] stopWords = {
            "là", "gì", "như", "thế nào", "khi nào", "ở đâu",
            "tôi", "bạn", "chúng ta", "họ", "này", "đó",
            "và", "hoặc", "nhưng", "vì", "nếu", "thì",
            "how", "what", "when", "where", "why", "is", "are", "the", "a", "an"
        };

        String lower = query.toLowerCase();
        for (String sw : stopWords) {
            lower = lower.replace(sw, " ");
        }

        // Lấy cụm từ dài nhất còn lại (heuristic: thường là keyword chính)
        String[] words = lower.trim().split("\\s+");
        String best = "";
        for (String w : words) {
            if (w.length() > best.length()) best = w;
        }

        // Fallback: dùng toàn bộ query nếu không trích được
        return best.isEmpty() ? query : best;
    }
}
