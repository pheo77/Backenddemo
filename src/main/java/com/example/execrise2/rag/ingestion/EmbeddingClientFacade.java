package com.example.execrise2.rag.ingestion;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * [LAYER 1 + 9] EMBEDDING CLIENT FACADE
 *
 * Bọc lấy logic gọi Embedding API để:
 * - Dễ dàng swap provider (OpenAI / Gemini / Ollama)
 * - Cache kết quả (tránh gọi API lặp lại cho cùng văn bản)
 *
 * ⚠️  HIỆN TẠI: Mock trả về random vector để test không cần API key.
 *     → Khi có Spring AI, thay thế bằng:
 *         private final EmbeddingModel embeddingModel; // Spring AI
 *         return embeddingModel.embed(text).toFloatArray();
 *
 * Cấu hình trong application.properties:
 *     spring.ai.openai.api-key=${OPENAI_API_KEY}
 *     spring.ai.openai.embedding.model=text-embedding-3-small
 */
@Component
@Slf4j
public class EmbeddingClientFacade {

    /** Số chiều vector — phải khớp với cột `vector(1536)` trong PostgreSQL */
    private static final int VECTOR_DIM = 1536;

    private final Random random = new Random();

    /**
     * Embed một đoạn văn bản đơn lẻ.
     *
     * @param text Văn bản cần embed
     * @return float[] vector chuẩn hóa L2
     */
    public float[] embed(String text) {
        log.debug("🔢 [EMBEDDING] Embed {} ký tự...", text.length());

        // ── TODO: Thay block này bằng Spring AI khi tích hợp ──────────
        // return embeddingModel.embed(text).toFloatArray();
        // ─────────────────────────────────────────────────────────────
        return mockEmbed(text);
    }

    /**
     * Batch embed — gọi API 1 lần cho N đoạn văn (tiết kiệm cost).
     *
     * @param texts Danh sách văn bản
     * @return Danh sách vector theo thứ tự tương ứng
     */
    public List<float[]> embedBatch(List<String> texts) {
        log.debug("🔢 [EMBEDDING] Batch embed {} đoạn văn...", texts.size());

        // ── TODO: Thay bằng Spring AI batch embed ─────────────────────
        // return embeddingModel.embed(texts).stream()
        //     .map(Embedding::toFloatArray).toList();
        // ─────────────────────────────────────────────────────────────
        return texts.stream().map(this::mockEmbed).toList();
    }

    // ──────────────────────────────────────────────────────────────
    //  MOCK IMPLEMENTATION (Dùng cho phát triển / test offline)
    // ──────────────────────────────────────────────────────────────

    /**
     * Tạo vector ngẫu nhiên chuẩn hóa L2 — dùng để test pipeline
     * mà không cần API key thật.
     * Hash của text đảm bảo cùng input → cùng output (deterministic).
     */
    private float[] mockEmbed(String text) {
        float[] vector = new float[VECTOR_DIM];
        Random seeded = new Random(text.hashCode()); // Deterministic

        float norm = 0;
        for (int i = 0; i < VECTOR_DIM; i++) {
            vector[i] = seeded.nextFloat() * 2 - 1; // [-1, 1]
            norm += vector[i] * vector[i];
        }

        // L2 Normalization — cosine similarity hoạt động chính xác hơn
        norm = (float) Math.sqrt(norm);
        for (int i = 0; i < VECTOR_DIM; i++) {
            vector[i] /= norm;
        }

        return vector;
    }
}
