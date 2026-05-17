package com.example.execrise2.ai_orchestrator.metrics;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * [Phase 4.3] OrchestratorMetrics — Thread-safe performance counters.
 *
 * Tracks per-provider:
 *   - totalRequests: Tổng số lần gọi
 *   - totalErrors:   Tổng số lần lỗi
 *   - totalLatencyMs: Tổng latency (để tính average)
 *   - racingWins:    Số lần thắng trong Speculative Racing
 *
 * Exposed via GET /api/ai/metrics
 */
@Component
public class OrchestratorMetrics {

    // ConcurrentHashMap → thread-safe, khởi tạo lazy per provider
    private final Map<String, ProviderStats> stats = new ConcurrentHashMap<>();

    /**
     * Ghi nhận 1 lần gọi API hoàn thành.
     *
     * @param provider  Tên provider ("GROQ" / "GEMINI" / ...)
     * @param latencyMs Thời gian xử lý (ms)
     * @param success   true = thành công, false = lỗi
     */
    public void recordCall(String provider, long latencyMs, boolean success) {
        getOrCreate(provider).record(latencyMs, success);
    }

    /**
     * Ghi nhận 1 lần thắng trong Speculative Racing.
     */
    public void recordRacingWin(String provider) {
        getOrCreate(provider).racingWins.incrementAndGet();
    }

    /**
     * Trả về summary JSON-ready map cho endpoint /api/ai/metrics.
     */
    public Map<String, Map<String, Object>> getSummary() {
        return stats.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().toMap()
                ));
    }

    /** Reset tất cả counters (dùng khi restart hoặc test). */
    public void reset() {
        stats.clear();
    }

    private ProviderStats getOrCreate(String provider) {
        return stats.computeIfAbsent(provider, k -> new ProviderStats());
    }

    // ──────────────────────────────────────────────────────────────

    static class ProviderStats {
        final AtomicLong totalRequests = new AtomicLong(0);
        final AtomicLong totalErrors   = new AtomicLong(0);
        final AtomicLong totalLatencyMs = new AtomicLong(0);
        final AtomicLong racingWins    = new AtomicLong(0);

        void record(long latencyMs, boolean success) {
            totalRequests.incrementAndGet();
            totalLatencyMs.addAndGet(latencyMs);
            if (!success) totalErrors.incrementAndGet();
        }

        Map<String, Object> toMap() {
            long reqs    = totalRequests.get();
            long avgLatency = reqs > 0 ? totalLatencyMs.get() / reqs : 0;
            return Map.of(
                "requests",    reqs,
                "errors",      totalErrors.get(),
                "errorRate",   reqs > 0 ? String.format("%.1f%%", 100.0 * totalErrors.get() / reqs) : "N/A",
                "avgLatency",  avgLatency + "ms",
                "racingWins",  racingWins.get()
            );
        }
    }
}
