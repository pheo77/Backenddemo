package com.example.execrise2.rag.retrieval;

import com.example.execrise2.rag.model.DocumentChunk;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * [LAYER 5] CONTEXT COMPRESSOR — Nén context trước khi nhét vào Prompt.
 *
 * Vấn đề: Top-10 chunk có thể chứa nhiều thông tin thừa, dài quá mức cho phép
 * của LLM (context window ~4000 tokens).
 *
 * Giải pháp: Trích xuất chỉ những câu thực sự liên quan đến câu hỏi.
 *
 * Implementation hiện tại: Heuristic (không dùng LLM).
 * TODO nâng cấp: Dùng LLM để tóm tắt từng chunk thay vì cắt cứng.
 */
@Service
@Slf4j
public class ContextCompressor {

    /** Số ký tự tối đa cho toàn bộ context block */
    private static final int MAX_CONTEXT_CHARS = 6000;
    /** Số câu tối đa lấy từ mỗi chunk */
    private static final int MAX_SENTENCES_PER_CHUNK = 5;

    /**
     * Nén danh sách chunk thành một chuỗi context gọn gàng.
     *
     * @param chunks   Danh sách chunk đã fuse + re-rank
     * @param question Câu hỏi để hướng dẫn trích xuất câu liên quan
     * @return Chuỗi context đã nén, sẵn sàng nhét vào Prompt
     */
    public String compress(List<DocumentChunk> chunks, String question) {
        log.debug("📦 [COMPRESSOR] Nén {} chunk cho câu hỏi: '{}'...",
            chunks.size(), question.substring(0, Math.min(50, question.length())));

        StringBuilder context = new StringBuilder();
        String[] queryWords = extractKeywords(question);

        for (int i = 0; i < chunks.size(); i++) {
            DocumentChunk chunk = chunks.get(i);
            String compressed = compressChunk(chunk.getContent(), queryWords);

            // Thêm header đánh số nguồn để AI có thể trích dẫn [Nguồn: X]
            String block = String.format("[Nguồn: %d] (Loại: %s, Ngôn ngữ: %s)\n%s\n\n",
                i + 1,
                chunk.getDocType() != null ? chunk.getDocType() : "unknown",
                chunk.getLanguage() != null ? chunk.getLanguage() : "unknown",
                compressed
            );

            // Kiểm tra giới hạn tổng context
            if (context.length() + block.length() > MAX_CONTEXT_CHARS) {
                log.debug("   Đã đạt giới hạn context ({} chars). Dừng ở chunk {}.", 
                    MAX_CONTEXT_CHARS, i);
                break;
            }

            context.append(block);
        }

        log.debug("   → Context cuối: {} ký tự từ {} chunk",
            context.length(), Math.min(chunks.size(), 
                (int) context.chars().filter(c -> c == '\n').count() / 3 + 1));

        return context.toString().trim();
    }

    // ──────────────────────────────────────────────────────────────
    //  PRIVATE HELPERS
    // ──────────────────────────────────────────────────────────────

    /**
     * Nén một chunk: Lấy các câu chứa keyword từ câu hỏi.
     * Nếu không tìm được, lấy N câu đầu tiên (fallback).
     */
    private String compressChunk(String content, String[] queryWords) {
        String[] sentences = content.split("(?<=[.!?。！？])\\s+");
        List<String> relevant = new ArrayList<>();

        for (String sentence : sentences) {
            if (isRelevant(sentence, queryWords)) {
                relevant.add(sentence.trim());
                if (relevant.size() >= MAX_SENTENCES_PER_CHUNK) break;
            }
        }

        // Fallback: Lấy 3 câu đầu nếu không tìm được câu liên quan
        if (relevant.isEmpty()) {
            for (int i = 0; i < Math.min(3, sentences.length); i++) {
                relevant.add(sentences[i].trim());
            }
        }

        return String.join(" ", relevant);
    }

    /**
     * Kiểm tra câu có chứa ít nhất 1 keyword từ câu hỏi không.
     */
    private boolean isRelevant(String sentence, String[] queryWords) {
        String lower = sentence.toLowerCase();
        for (String word : queryWords) {
            if (word.length() >= 2 && lower.contains(word)) return true;
        }
        return false;
    }

    /**
     * Trích xuất keyword từ câu hỏi (bỏ stop words ngắn).
     */
    private String[] extractKeywords(String question) {
        return question.toLowerCase()
            .split("\\s+")
            .clone();
    }
}
