package com.example.execrise2.ai_orchestrator.racing;

import com.example.execrise2.ai_orchestrator.adapter.AiProviderAdapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * [Trụ cột 1] SPECULATIVE PARALLEL RACING — Fix: Cancel task thua.
 *
 * Bug cũ: CompletableFuture.anyOf() KHÔNG cancel task chậm hơn.
 *   → Groq thắng sau 0.8s, Gemini VẪN chạy đến 3s → tốn token + chiếm thread.
 *
 * Fix mới:
 *   1. Bọc mỗi task trong CompletableFuture riêng để track từng provider.
 *   2. Sau anyOf() → xác định task nào thắng → cancel task còn lại.
 *   3. Trả RaceResult (answer + winnerProvider + latencyMs + loserCancelled).
 *   4. Ghi log rõ ràng: "🏆 WINNER=GROQ latency=842ms | Cancelled GEMINI task"
 */
@Service
@Slf4j
public class SpeculativeRacingService {

    /**
     * Race 2 providers song song. Provider nào trả lời trước → thắng.
     * Provider thua bị cancel ngay lập tức.
     *
     * @param provider1 Provider thứ nhất (thường là GROQ)
     * @param provider2 Provider thứ hai (thường là GEMINI)
     * @param prompt    Câu hỏi gửi tới cả 2
     * @return RaceResult với đầy đủ thông tin winner + loser cancel status
     */
    public RaceResult raceProviders(AiProviderAdapter provider1,
                                    AiProviderAdapter provider2,
                                    String prompt) {
        long startTime = System.currentTimeMillis();
        log.info("🏁 [RACE] Bắt đầu: {} vs {}", provider1.getProviderName(), provider2.getProviderName());

        // Wrapper chứa thêm provider name để biết ai thắng
        CompletableFuture<String[]> task1 = provider1
                .generateResponseAsync("System prompt: Answer concisely.", prompt)
                .thenApply(answer -> new String[]{ provider1.getProviderName(), answer });

        CompletableFuture<String[]> task2 = provider2
                .generateResponseAsync("System prompt: Answer concisely.", prompt)
                .thenApply(answer -> new String[]{ provider2.getProviderName(), answer });

        try {
            // Chờ provider nào xong trước
            @SuppressWarnings("unchecked")
            CompletableFuture<Object> race = CompletableFuture.anyOf(task1, task2);
            String[] winnerData = (String[]) race.get(5, TimeUnit.SECONDS);

            long latencyMs = System.currentTimeMillis() - startTime;
            String winnerName = winnerData[0];
            String winnerAnswer = winnerData[1];

            // ─── CANCEL TASK THUA ───────────────────────────────────
            boolean loserCancelled = false;
            if (winnerName.equals(provider1.getProviderName())) {
                // Provider1 thắng → cancel task2
                loserCancelled = task2.cancel(true);
                log.info("🏆 [RACE] WINNER={} latency={}ms | {} task {} (GEMINI)",
                        winnerName, latencyMs,
                        loserCancelled ? "Cancelled" : "Already done",
                        provider2.getProviderName());
            } else {
                // Provider2 thắng → cancel task1
                loserCancelled = task1.cancel(true);
                log.info("🏆 [RACE] WINNER={} latency={}ms | {} task {} (GROQ)",
                        winnerName, latencyMs,
                        loserCancelled ? "Cancelled" : "Already done",
                        provider1.getProviderName());
            }

            return new RaceResult(winnerAnswer, winnerName, latencyMs, loserCancelled);

        } catch (Exception e) {
            long latencyMs = System.currentTimeMillis() - startTime;
            log.error("❌ [RACE] Cả 2 provider đều thất bại sau {}ms: {}", latencyMs, e.getMessage());

            // Cố cancel cả 2 để không leak thread
            task1.cancel(true);
            task2.cancel(true);

            return RaceResult.failed(
                "⏳ Tất cả AI provider đều hết thời gian chờ. Vui lòng thử lại.",
                latencyMs
            );
        }
    }
}
