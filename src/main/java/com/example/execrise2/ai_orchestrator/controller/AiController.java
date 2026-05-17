package com.example.execrise2.ai_orchestrator.controller;

import com.example.execrise2.ai_orchestrator.adapter.AiProviderAdapter;
import com.example.execrise2.ai_orchestrator.adapter.GeminiAdapter;
import com.example.execrise2.ai_orchestrator.adapter.GroqAdapter;
import com.example.execrise2.ai_orchestrator.cache.AiSessionManager;
import com.example.execrise2.ai_orchestrator.guardrails.ContentGuardrailService;
import com.example.execrise2.ai_orchestrator.metrics.OrchestratorMetrics;
import com.example.execrise2.ai_orchestrator.racing.RaceResult;
import com.example.execrise2.ai_orchestrator.racing.SpeculativeRacingService;
import com.example.execrise2.ai_orchestrator.router.AiRouterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * [UPGRADED] AiController — Tích hợp đầy đủ 4 Phase nâng cấp.
 *
 * Endpoints:
 *   GET /api/ai/router      → Routing + Guardrails + Sticky Session + CB + Metrics
 *   GET /api/ai/racing      → Speculative Racing (cancel loser, update session)
 *   GET /api/ai/clear-session
 *   GET /api/ai/pool-stats  → Groq + Gemini pool status [PROTECTED]
 *   GET /api/ai/pool-reset  → Reset all pools [PROTECTED]
 *   GET /api/ai/metrics     → Performance metrics per provider [PROTECTED]
 *   GET /api/ai/cb-stats    → Circuit Breaker states [PROTECTED]
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@Slf4j
public class AiController {

    private final AiRouterService          routerService;
    private final SpeculativeRacingService racingService;
    private final ContentGuardrailService  guardrailService;
    private final AiSessionManager         sessionManager;
    private final GroqAdapter              groqAdapter;
    private final GeminiAdapter            geminiAdapter;
    private final OrchestratorMetrics      metrics;

    // ──────────────────────────────────────────────────────────────
    //  ROUTER — Routing + CB + Guardrails + Sticky Session
    // ──────────────────────────────────────────────────────────────

    @GetMapping("/router")
    public String testRouter(
            @RequestParam(defaultValue = "User123") String userId,
            @RequestParam(defaultValue = "Xin chào") String message) {
        long start = System.currentTimeMillis();

        try {
            // 1. Guardrails (với userId để rate-limit per-user)
            guardrailService.validateMessage(message, userId);

            // 2. Routing (Sticky Session + Complexity Score + CB check)
            AiProviderAdapter provider = routerService.routeToBestProvider(userId, message);
            String providerName = provider != null ? provider.getProviderName() : "NONE";

            // 3. Execute với fallback + CB + metrics
            String result = routerService.executeWithFallback(provider, "Trả lời ngắn gọn bằng tiếng Việt.", message).join();

            long latency = System.currentTimeMillis() - start;

            return "<h1>🎛️ AI Router V5.0 — Production Ready</h1>" +
                   "<p><b>User:</b> " + userId + "</p>" +
                   "<p><b>Câu hỏi:</b> " + message + "</p>" +
                   "<p><b>Provider:</b> <span style='color:blue'>" + providerName + "</span></p>" +
                   "<p><b>Latency:</b> " + latency + "ms</p>" +
                   "<p><b>Kết quả:</b> " + result + "</p>";

        } catch (SecurityException e) {
            return "<h1 style='color:red'>🛡️ Guardrail kích hoạt!</h1><p>" + e.getMessage() + "</p>";
        } catch (Exception e) {
            log.error("Router error: {}", e.getMessage());
            return "<h1 style='color:orange'>⚠️ Lỗi hệ thống</h1><p>" + e.getMessage() + "</p>";
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  RACING — Speculative Racing với cancel loser + update session
    // ──────────────────────────────────────────────────────────────

    /**
     * Fix 1.1 + 4.2: Racing trả RaceResult, cancel loser, update sticky session.
     * URL: http://localhost:8080/api/ai/racing?message=hello&userId=User123
     */
    @GetMapping("/racing")
    public String testRacing(
            @RequestParam(defaultValue = "Hãy phân tích câu này") String message,
            @RequestParam(defaultValue = "User123") String userId) {

        RaceResult result = racingService.raceProviders(groqAdapter, geminiAdapter, message);

        // Fix 4.2: Cập nhật sticky session với winner
        if (!"NONE".equals(result.winnerProvider())) {
            sessionManager.saveUserProvider(userId, result.winnerProvider());
        }

        // Ghi metrics
        metrics.recordRacingWin(result.winnerProvider());

        return "<h1>🏁 Speculative Racing — Fixed (Cancel Loser)</h1>" +
               "<p><b>Câu hỏi:</b> " + message + "</p>" +
               "<p><b>🏆 Winner:</b> <span style='color:green'>" + result.winnerProvider() + "</span></p>" +
               "<p><b>⏱️ Latency:</b> " + result.latencyMs() + "ms</p>" +
               "<p><b>🚫 Loser Cancelled:</b> " + (result.loserCancelled() ? "✅ Đã cancel (tiết kiệm token)" : "⚠️ Đã xong trước") + "</p>" +
               "<p><b>Kết quả:</b> " + result.answer() + "</p>" +
               "<p><small>Session của User " + userId + " đã được cập nhật → " + result.winnerProvider() + "</small></p>";
    }

    // ──────────────────────────────────────────────────────────────
    //  SESSION MANAGEMENT
    // ──────────────────────────────────────────────────────────────

    @GetMapping("/clear-session")
    public String clearSession(@RequestParam(defaultValue = "User123") String userId) {
        sessionManager.clearSession(userId);
        return "<h1>✅ Đã xóa session của User: " + userId + "</h1>";
    }

    // ──────────────────────────────────────────────────────────────
    //  ADMIN ENDPOINTS (Bảo vệ bởi AdminApiKeyFilter: X-Admin-Token)
    // ──────────────────────────────────────────────────────────────

    /** GET /api/ai/pool-stats — Groq + Gemini key status chi tiết */
    @GetMapping("/pool-stats")
    public Map<String, Object> poolStats() {
        return Map.of(
            "groq",   groqAdapter.getPoolStats(),
            "gemini", geminiAdapter.getPoolStats()
        );
    }

    /** GET /api/ai/pool-reset — Reset cả Groq + Gemini key Pool về ACTIVE */
    @GetMapping("/pool-reset")
    public String poolReset() {
        groqAdapter.resetPool();
        geminiAdapter.resetPool();
        return "<h1>✅ Đã reset toàn bộ Groq + Gemini Key Pool về ACTIVE</h1>" +
               "<p>Tất cả key sẵn sàng nhận request mới.</p>";
    }

    /** GET /api/ai/metrics — Performance metrics */
    @GetMapping("/metrics")
    public Map<String, Map<String, Object>> metricsEndpoint() {
        return metrics.getSummary();
    }

    /** GET /api/ai/cb-stats — Circuit Breaker states */
    @GetMapping("/cb-stats")
    public List<Map<String, Object>> circuitBreakerStats() {
        return routerService.getCircuitBreakerStats();
    }
}
