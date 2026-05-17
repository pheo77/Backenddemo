package com.example.execrise2.rag.selfrag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * [LAYER 9 — NÂNG CAO] SELF-RAG SERVICE
 *
 * Cho phép hệ thống RAG tự đánh giá chất lượng kết quả của mình:
 *
 * 1. Relevance Grading: Chunk có thực sự liên quan đến câu hỏi không? (0.0 - 1.0)
 * 2. Hallucination Check: Câu trả lời AI có "bịa" thêm thông tin không có trong context?
 *
 * ⚠️  MOCK implementation dùng heuristic đơn giản.
 *     → Thay bằng Spring AI ChatClient để đạt độ chính xác cao hơn:
 * <pre>
 *   String verdict = chatClient.prompt()
 *       .system("Chấm điểm mức độ liên quan từ 0.0 đến 1.0. Chỉ trả về số, không giải thích.")
 *       .user("Câu hỏi: " + question + "\n\nChunk: " + chunk)
 *       .call().content();
 *   return Double.parseDouble(verdict.trim());
 * </pre>
 */
@Service
@Slf4j
public class SelfRagService {

    // Ngưỡng chấp nhận (có thể cấu hình từ application.properties)
    private static final double RELEVANCE_THRESHOLD = 0.3;

    /**
     * Chấm điểm mức độ liên quan của một chunk với câu hỏi.
     *
     * @param question Câu hỏi người dùng
     * @param chunk    Nội dung chunk cần đánh giá
     * @return Điểm relevance [0.0, 1.0] — cao hơn = liên quan hơn
     */
    public double gradeRelevance(String question, String chunk) {
        if (question == null || chunk == null) return 0.0;

        // ── TODO: Thay bằng LLM call ─────────────────────────────────
        // String verdict = chatClient.prompt()
        //     .system("Chấm điểm mức độ liên quan của đoạn văn với câu hỏi. " +
        //             "Chỉ trả về số từ 0.0 đến 1.0. KHÔNG giải thích thêm.")
        //     .user("Câu hỏi: " + question + "\n\nĐoạn văn: " + chunk)
        //     .call().content();
        // return Double.parseDouble(verdict.trim());
        // ─────────────────────────────────────────────────────────────

        // MOCK: Tính điểm bằng keyword overlap (heuristic đơn giản)
        return computeKeywordOverlapScore(question, chunk);
    }

    /**
     * Kiểm tra hallucination: Câu trả lời AI có hoàn toàn dựa trên context không?
     *
     * @param answer  Câu trả lời do LLM sinh ra
     * @param context Context đã được cung cấp cho LLM
     * @return true nếu câu trả lời không bịa thêm thông tin
     */
    public boolean isGrounded(String answer, String context) {
        if (answer == null || context == null) return false;

        // ── TODO: Thay bằng LLM call ─────────────────────────────────
        // String verdict = chatClient.prompt()
        //     .system("Kiểm tra xem câu trả lời có hoàn toàn dựa trên ngữ cảnh không. " +
        //             "Trả về YES nếu không có thông tin bịa thêm, NO nếu có.")
        //     .user("Ngữ cảnh:\n" + context + "\n\nCâu trả lời:\n" + answer)
        //     .call().content();
        // return verdict.trim().toUpperCase().startsWith("YES");
        // ─────────────────────────────────────────────────────────────

        // MOCK: Kiểm tra heuristic — nếu câu trả lời chứa từ "MOCK" thì bỏ qua grounding check
        if (answer.contains("[MOCK")) return true;

        // Đơn giản: kiểm tra xem câu trả lời có quá ngắn so với context không
        // (câu trả lời rất ngắn thường là "tôi không biết" → an toàn)
        if (answer.length() < 50) return true;

        // Heuristic: Nếu câu trả lời đề cập đến nguồn [Nguồn: X] → có vẻ grounded
        boolean hasCitation = answer.contains("[Nguồn:");
        log.debug("   [Self-RAG] Hallucination check — hasCitation={}", hasCitation);
        return hasCitation;
    }

    // ──────────────────────────────────────────────────────────────
    //  MOCK HELPERS
    // ──────────────────────────────────────────────────────────────

    /**
     * Tính điểm relevance bằng tỉ lệ keyword overlap (Jaccard-inspired).
     * Đây là approximation đơn giản, không chính xác bằng LLM grading.
     */
    private double computeKeywordOverlapScore(String question, String chunk) {
        String[] qWords = tokenize(question);
        String chunkLower = chunk.toLowerCase();

        int matches = 0;
        for (String word : qWords) {
            if (word.length() >= 2 && chunkLower.contains(word)) {
                matches++;
            }
        }

        if (qWords.length == 0) return 0.0;
        double score = (double) matches / qWords.length;
        
        // Bonus nếu chunk chứa nhiều keyword liên tiếp nhau
        if (score > 0.3) score = Math.min(1.0, score * 1.2);

        return score;
    }

    private String[] tokenize(String text) {
        return text.toLowerCase()
            .replaceAll("[^a-zA-Z0-9\\u3040-\\u30FF\\u4E00-\\u9FFF\\u0100-\\u024F ]", "")
            .split("\\s+");
    }
}
