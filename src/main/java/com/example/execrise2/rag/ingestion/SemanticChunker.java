package com.example.execrise2.rag.ingestion;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * [LAYER 1] SEMANTIC CHUNKER — Cắt văn bản thành chunk theo ranh giới ngữ nghĩa.
 *
 * Chiến lược mặc định: Sliding Window + Sentence Boundary Detection.
 * - Ưu tiên cắt tại dấu chấm/xuống dòng (ranh giới câu thực sự).
 * - Overlap 10% giữa các chunk để không mất ngữ cảnh ở ranh giới.
 *
 * Nâng cấp tương lai:
 *   - Semantic Chunking: Dùng Embedding để phát hiện "đoạn ngữ nghĩa mới"
 *   - Hierarchical Chunking: Tạo chunk lớn (summary) + chunk nhỏ (detail)
 */
@Component
@Slf4j
public class SemanticChunker {

    private static final int DEFAULT_CHUNK_SIZE  = 512;  // Ký tự
    private static final int DEFAULT_OVERLAP     = 50;   // Ký tự overlap
    private static final int MIN_CHUNK_SIZE      = 100;  // Bỏ chunk quá nhỏ

    /**
     * Cắt văn bản thành danh sách chunk với overlap.
     *
     * @param text Văn bản đầu vào (đã làm sạch)
     * @return Danh sách chunk sẵn sàng để embed
     */
    public List<String> chunk(String text) {
        return chunk(text, DEFAULT_CHUNK_SIZE, DEFAULT_OVERLAP);
    }

    /**
     * Cắt với kích thước tùy chỉnh.
     */
    public List<String> chunk(String text, int chunkSize, int overlap) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        List<String> chunks = new ArrayList<>();
        // Ưu tiên tách theo câu trước
        String[] sentences = text.split("(?<=[.!?。！？\\n])\\s*");

        StringBuilder current = new StringBuilder();

        for (String sentence : sentences) {
            sentence = sentence.trim();
            if (sentence.isEmpty()) continue;

            // Nếu thêm câu mới vào chunk hiện tại sẽ vượt giới hạn → flush
            if (current.length() + sentence.length() > chunkSize && current.length() > 0) {
                String chunkText = current.toString().trim();
                if (chunkText.length() >= MIN_CHUNK_SIZE) {
                    chunks.add(chunkText);
                }

                // Giữ lại phần overlap (các từ cuối)
                String overlapText = extractOverlap(current.toString(), overlap);
                current = new StringBuilder(overlapText);
            }

            current.append(sentence).append(" ");
        }

        // Flush chunk cuối cùng
        String remaining = current.toString().trim();
        if (remaining.length() >= MIN_CHUNK_SIZE) {
            chunks.add(remaining);
        }

        log.debug("✂️  [CHUNKER] {} ký tự → {} chunk (size={}, overlap={})",
            text.length(), chunks.size(), chunkSize, overlap);
        return chunks;
    }

    // ──────────────────────────────────────────────────────────────
    //  HELPERS
    // ──────────────────────────────────────────────────────────────

    /**
     * Lấy N ký tự cuối của đoạn văn làm overlap cho chunk tiếp theo.
     */
    private String extractOverlap(String text, int overlapSize) {
        if (text.length() <= overlapSize) return text;
        // Tìm vị trí khoảng trắng gần nhất để không cắt giữa từ
        String tail = text.substring(text.length() - overlapSize);
        int spaceIdx = tail.indexOf(' ');
        return spaceIdx > 0 ? tail.substring(spaceIdx + 1) : tail;
    }
}
