package com.example.execrise2.ai_orchestrator.racing;

/**
 * [VALUE OBJECT] RaceResult — Kết quả cuộc đua Speculative Racing.
 *
 * Thay vì chỉ trả String, giờ biết được:
 * - answer:         Câu trả lời từ provider thắng
 * - winnerProvider: Tên provider thắng ("GROQ" / "GEMINI")
 * - latencyMs:      Thời gian từ lúc bắt đầu đến khi có kết quả
 * - loserCancelled: Provider thua có bị cancel thành công không
 */
public record RaceResult(
    String answer,
    String winnerProvider,
    long   latencyMs,
    boolean loserCancelled
) {
    /** Factory method cho trường hợp race thất bại hoàn toàn */
    public static RaceResult failed(String errorMessage, long latencyMs) {
        return new RaceResult(errorMessage, "NONE", latencyMs, false);
    }
}
