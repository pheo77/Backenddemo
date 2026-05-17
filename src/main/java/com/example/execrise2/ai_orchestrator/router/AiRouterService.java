package com.example.execrise2.ai_orchestrator.router;

import com.example.execrise2.ai_orchestrator.adapter.AiProviderAdapter;
import com.example.execrise2.ai_orchestrator.cache.AiSessionManager;
import com.example.execrise2.ai_orchestrator.circuitbreaker.ProviderCircuitBreaker;
import com.example.execrise2.ai_orchestrator.metrics.OrchestratorMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * [Phase 1.3 + 3.1 + 4.1] AiRouterService — Fix 3 vấn đề:
 *
 * Fix 1.3: getProviderByName() không crash khi provider unhealthy
 *   Cũ: orElseThrow() → crash 500 nếu tất cả provider sập.
 *   Mới: orElse(null) + null-check → trả graceful error message.
 *
 * Fix 3.1: Circuit Breaker trước mỗi API call
 *   - allowRequest() = false → fail-fast < 5ms thay vì chờ timeout 10s.
 *   - recordSuccess/Failure() để CB tự transition state.
 *
 * Fix 4.1: evaluateComplexity() thông minh hơn
 *   Scoring 0-10 dựa trên độ dài, từ kỹ thuật, code snippet, ngôn ngữ.
 *   Route: score<4 → Gemini | 4-6 → healthiest provider | >6 → Groq.
 */
@Service
@Slf4j
public class AiRouterService {

    private final List<AiProviderAdapter> providers;
    private final AiSessionManager sessionManager;
    private final OrchestratorMetrics metrics;

    // Một Circuit Breaker per provider
    private final Map<String, ProviderCircuitBreaker> breakers = new ConcurrentHashMap<>();

    @Autowired
    public AiRouterService(List<AiProviderAdapter> providers,
                           AiSessionManager sessionManager,
                           OrchestratorMetrics metrics) {
        this.providers      = providers;
        this.sessionManager = sessionManager;
        this.metrics        = metrics;

        // Khởi tạo CB cho mỗi provider
        providers.forEach(p -> breakers.put(
            p.getProviderName(),
            new ProviderCircuitBreaker(p.getProviderName(), 5, 30)
        ));
        log.info("✅ [Router] Đã tạo Circuit Breaker cho: {}", breakers.keySet());
    }

    // ──────────────────────────────────────────────────────────────
    //  ROUTING — Sticky Session + Complexity Score + CB check
    // ──────────────────────────────────────────────────────────────

    public AiProviderAdapter routeToBestProvider(String userId, String userMessage) {
        // 1. Sticky Session — tiếp tục provider cũ để tận dụng KV-Cache
        String prevProvider = sessionManager.getPreviousProvider(userId);
        if (prevProvider != null) {
            AiProviderAdapter prev = getProviderByName(prevProvider);
            if (prev != null && circuitAllows(prev.getProviderName())) {
                log.info("🔄 [Router] Sticky: User {} → {}", userId, prevProvider);
                return prev;
            }
            // Provider cũ bị CB OPEN → clear session, chọn lại
            sessionManager.clearSession(userId);
            log.warn("⚠️  [Router] Sticky provider {} bị CB block → chọn lại", prevProvider);
        }

        // 2. Complexity scoring → routing
        int score = evaluateComplexity(userMessage);
        AiProviderAdapter selected;

        if (score < 4) {
            selected = getHealthyProvider("GEMINI");
            log.info("→ [Router] Score={} → GEMINI (nhanh, đơn giản)", score);
        } else if (score > 6) {
            selected = getHealthyProvider("GROQ");
            log.info("→ [Router] Score={} → GROQ (phức tạp, reasoning)", score);
        } else {
            // Score trung bình → chọn provider healthy + CB CLOSED
            selected = providers.stream()
                    .filter(p -> p.isHealthy() && circuitAllows(p.getProviderName()))
                    .findFirst()
                    .orElse(null);
            log.info("→ [Router] Score={} → Dynamic (provider healthy đầu tiên)", score);
        }

        if (selected == null) {
            log.warn("⚠️  [Router] Không có provider nào khỏe — dùng first provider");
            selected = providers.isEmpty() ? null : providers.get(0);
        }

        if (selected != null) {
            sessionManager.saveUserProvider(userId, selected.getProviderName());
        }
        return selected;
    }

    // ──────────────────────────────────────────────────────────────
    //  EXECUTE với Circuit Breaker + Auto-Fallback + Metrics
    // ──────────────────────────────────────────────────────────────

    public CompletableFuture<String> executeWithFallback(AiProviderAdapter primary,
                                                          String systemPrompt,
                                                          String userMessage) {
        if (primary == null) {
            return CompletableFuture.completedFuture("⏳ Hệ thống AI đang quá tải. Vui lòng thử lại sau.");
        }

        String providerName = primary.getProviderName();
        ProviderCircuitBreaker cb = breakers.get(providerName);

        // Circuit Breaker check — fail-fast nếu CB OPEN
        if (cb != null && !cb.allowRequest()) {
            log.warn("🔴 [Router] CB-{} OPEN → Fail-fast → Fallback", providerName);
            return fallback(primary, systemPrompt, userMessage);
        }

        long start = System.currentTimeMillis();

        return primary.generateResponseAsync(systemPrompt, userMessage)
                .whenComplete((result, error) -> {
                    long latency = System.currentTimeMillis() - start;
                    boolean ok   = error == null;
                    metrics.recordCall(providerName, latency, ok);
                    if (cb != null) {
                        if (ok) cb.recordSuccess(); else cb.recordFailure();
                    }
                })
                .exceptionally(ex -> {
                    log.warn("⚠️  [Router] {} lỗi → Auto-Fallback | {}", providerName, ex.getMessage());
                    return fallback(primary, systemPrompt, userMessage).join();
                });
    }

    // ──────────────────────────────────────────────────────────────
    //  HELPERS
    // ──────────────────────────────────────────────────────────────

    /**
     * Fix 1.3: Trả null thay vì throw khi không tìm thấy / provider unhealthy.
     */
    public AiProviderAdapter getProviderByName(String name) {
        return providers.stream()
                .filter(p -> p.getProviderName().equalsIgnoreCase(name) && p.isHealthy())
                .findFirst()
                .orElse(null); // Không crash nữa
    }

    private AiProviderAdapter getHealthyProvider(String preferredName) {
        AiProviderAdapter preferred = getProviderByName(preferredName);
        if (preferred != null && circuitAllows(preferredName)) return preferred;

        // Fallback sang provider khác
        return providers.stream()
                .filter(p -> p.isHealthy() && circuitAllows(p.getProviderName()))
                .findFirst()
                .orElse(null);
    }

    private boolean circuitAllows(String providerName) {
        ProviderCircuitBreaker cb = breakers.get(providerName);
        return cb == null || cb.allowRequest();
    }

    private CompletableFuture<String> fallback(AiProviderAdapter failed,
                                                String systemPrompt,
                                                String userMessage) {
        AiProviderAdapter backup = providers.stream()
                .filter(p -> !p.getProviderName().equals(failed.getProviderName())
                        && p.isHealthy()
                        && circuitAllows(p.getProviderName()))
                .findFirst()
                .orElse(null);

        if (backup == null) {
            return CompletableFuture.completedFuture(
                "⏳ Tất cả AI provider đang quá tải. Vui lòng thử lại sau 1-2 phút."
            );
        }

        log.info("🔄 [Router] Fallback: {} → {}", failed.getProviderName(), backup.getProviderName());
        return backup.generateResponseAsync(systemPrompt, userMessage)
                .thenApply(r -> r + " [via " + backup.getProviderName() + " fallback]");
    }

    /**
     * Fix 4.1: Complexity scoring thông minh (0–10).
     *
     * | Tiêu chí                         | Điểm |
     * |----------------------------------|------|
     * | Độ dài > 100 ký tự               | +2   |
     * | Có ? nhiều lần (câu hỏi phức)    | +1   |
     * | Từ kỹ thuật: tại sao/phân tích   | +3   |
     * | Code snippet (backtick, {, })    | +2   |
     * | Chứa số                          | +1   |
     * | Tiếng Anh (>60% Latin chars)     | +1   |
     */
    private int evaluateComplexity(String message) {
        if (message == null || message.isBlank()) return 1;

        int score = 0;
        String lower = message.toLowerCase();

        // Độ dài
        if (message.length() > 100) score += 2;
        else if (message.length() > 50) score += 1;

        // Nhiều dấu hỏi
        long questionMarks = message.chars().filter(c -> c == '?').count();
        if (questionMarks > 1) score += 1;

        // Từ kỹ thuật / phân tích
        List<String> complexWords = List.of(
            "tại sao", "phân tích", "so sánh", "explain", "why", "analyze",
            "compare", "giải thích", "chứng minh", "evaluate", "design", "architect"
        );
        if (complexWords.stream().anyMatch(lower::contains)) score += 3;

        // Code snippet
        if (lower.contains("`") || lower.contains("{") || lower.contains("}") || lower.contains("=>")) score += 2;

        // Chứa số
        if (message.matches(".*\\d+.*")) score += 1;

        // Tiếng Anh (Latin chars > 60%)
        long latinCount = message.chars().filter(c -> c >= 'a' && c <= 'z').count();
        if (latinCount > message.length() * 0.6) score += 1;

        log.debug("📊 [Router] Complexity score={} for: '{}'...",
                score, message.substring(0, Math.min(40, message.length())));
        return Math.min(score, 10);
    }

    /** Lấy trạng thái tất cả Circuit Breakers. */
    public List<Map<String, Object>> getCircuitBreakerStats() {
        return breakers.values().stream().map(ProviderCircuitBreaker::toStats).toList();
    }
}
