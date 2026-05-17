package com.example.execrise2.ai_orchestrator.adapter;

import java.util.concurrent.CompletableFuture;

/**
 * [Layer 3] UNIVERSAL ADAPTER
 * Interface chuẩn hóa giúp hệ thống không bị phụ thuộc vào một AI duy nhất.
 */
public interface AiProviderAdapter {
    /**
     * Tên của nhà cung cấp (VD: "GROQ", "GEMINI", "DEEPSEEK")
     */
    String getProviderName();

    /**
     * Gửi yêu cầu tới AI và trả về kết quả dưới dạng Bất đồng bộ (Async).
     * Điều này rất quan trọng để làm "Speculative Racing".
     */
    CompletableFuture<String> generateResponseAsync(String systemPrompt, String userMessage);
    
    /**
     * Đánh giá xem Provider này có đang "khỏe" không.
     * Dùng cho Latency-Aware Balancing & Auto-Fallback.
     */
    boolean isHealthy();
}
