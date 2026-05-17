package com.example.execrise2.rag.retrieval;

import com.example.execrise2.rag.model.DocumentChunk;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * [LAYER 5] RRF FUSION SERVICE — Trộn kết quả từ 2 luồng tìm kiếm.
 *
 * Thuật toán: Reciprocal Rank Fusion (RRF)
 * ─────────────────────────────────────────
 * Score(d) = Σ  1 / (k + rank(d))
 *
 * - k = 60 (hằng số chuẩn từ paper gốc của Cormack et al. 2009)
 * - rank(d) là vị trí của chunk d trong từng danh sách (bắt đầu từ 1)
 * - Chunk xuất hiện trong cả 2 danh sách sẽ có điểm cộng dồn → được ưu tiên
 *
 * Ưu điểm so với Linear Combination:
 * - Không cần normalize score từ 2 nguồn khác nhau
 * - Robust với outlier (chunk hạng 1 không áp đảo quá mức)
 */
@Service
@Slf4j
public class RrfFusionService {

    /** Hằng số RRF — giá trị 60 được khuyến nghị trong paper gốc */
    private static final int RRF_K = 60;

    /** Số chunk tối đa giữ lại sau fusion */
    private static final int DEFAULT_FINAL_TOP = 10;

    /**
     * Trộn 2 danh sách kết quả bằng RRF, giữ Top-10.
     *
     * @param vectorChunks Kết quả từ Vector Search (đã xếp theo cosine similarity)
     * @param bm25Chunks   Kết quả từ BM25 Search (đã xếp theo BM25 score)
     * @return Top-10 chunk tổng hợp, xếp theo RRF score giảm dần
     */
    public List<DocumentChunk> fuse(List<DocumentChunk> vectorChunks,
                                    List<DocumentChunk> bm25Chunks) {
        return fuse(vectorChunks, bm25Chunks, DEFAULT_FINAL_TOP);
    }

    public List<DocumentChunk> fuse(List<DocumentChunk> vectorChunks,
                                    List<DocumentChunk> bm25Chunks,
                                    int finalTop) {
        log.debug("🔀 [RRF] Trộn {} vector chunk + {} BM25 chunk", 
            vectorChunks.size(), bm25Chunks.size());

        // Map: chunkId → RRF score tổng hợp
        Map<UUID, Double> scores = new HashMap<>();

        // Map: chunkId → DocumentChunk object (để lấy lại sau khi sắp xếp)
        Map<UUID, DocumentChunk> chunkMap = new HashMap<>();

        // Đóng góp điểm từ danh sách Vector
        addRrfScores(vectorChunks, scores, chunkMap);

        // Đóng góp điểm từ danh sách BM25 (điểm cộng dồn nếu chunk đã xuất hiện)
        addRrfScores(bm25Chunks, scores, chunkMap);

        // Sắp xếp theo điểm RRF tổng hợp (giảm dần) và lấy Top-N
        List<DocumentChunk> result = scores.entrySet().stream()
            .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
            .limit(finalTop)
            .map(entry -> chunkMap.get(entry.getKey()))
            .toList();

        log.debug("   → Sau RRF fusion: {} chunk được chọn", result.size());

        // Log top 3 để debug
        if (log.isDebugEnabled()) {
            scores.entrySet().stream()
                .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
                .limit(3)
                .forEach(e -> log.debug("      [Top chunk] id={} score={:.4f}",
                    e.getKey().toString().substring(0, 8), e.getValue()));
        }

        return result;
    }

    // ──────────────────────────────────────────────────────────────
    //  PRIVATE HELPER
    // ──────────────────────────────────────────────────────────────

    /**
     * Tính và cộng dồn RRF score cho từng chunk trong một danh sách.
     * rank = 1 cho phần tử đầu tiên (top), rank = n cho phần tử cuối.
     */
    private void addRrfScores(List<DocumentChunk> list,
                               Map<UUID, Double> scores,
                               Map<UUID, DocumentChunk> chunkMap) {
        for (int rank = 0; rank < list.size(); rank++) {
            DocumentChunk chunk = list.get(rank);
            UUID id = chunk.getId();
            double rrfScore = 1.0 / (RRF_K + rank + 1);  // rank+1 vì 0-indexed
            scores.merge(id, rrfScore, Double::sum);       // Cộng dồn nếu đã có
            chunkMap.put(id, chunk);                       // Lưu object
        }
    }
}
