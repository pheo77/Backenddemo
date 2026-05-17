package com.example.execrise2.ai_orchestrator.circuitbreaker;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * [Phase 3.1] CIRCUIT BREAKER — State Machine 3 trạng thái (không dùng Resilience4j).
 *
 * Trạng thái:
 *   CLOSED   → Mọi request đi qua bình thường
 *   OPEN     → Reject ngay lập tức (fail-fast < 5ms), không chờ timeout
 *   HALF_OPEN → Cho 1 request thử, nếu OK → CLOSED, nếu lỗi → OPEN lại
 *
 * Transition:
 *   CLOSED  →(≥5 lỗi liên tiếp trong 30s)→ OPEN
 *   OPEN    →(sau 30s recovery)→ HALF_OPEN
 *   HALF_OPEN →(success)→ CLOSED
 *   HALF_OPEN →(failure)→ OPEN (reset recovery timer)
 *
 * Tác dụng thực tế:
 *   Nếu Groq sập → mỗi request chờ full 10s timeout × 100 users = 1000s thread blocked.
 *   Với CB: sau 5 lỗi → OPEN → request tiếp theo fail-fast < 5ms → fallback Gemini ngay.
 */
@Slf4j
public class ProviderCircuitBreaker {

    public enum CircuitState { CLOSED, OPEN, HALF_OPEN }

    private final String providerName;
    private final int    failureThreshold;         // Default: 5
    private final long   recoveryWindowSeconds;    // Default: 30s

    private volatile CircuitState state      = CircuitState.CLOSED;
    private final AtomicInteger  failureCount = new AtomicInteger(0);
    private final AtomicInteger  successCount = new AtomicInteger(0);
    private volatile Instant     openedAt     = null;

    public ProviderCircuitBreaker(String providerName, int failureThreshold, long recoveryWindowSeconds) {
        this.providerName          = providerName;
        this.failureThreshold      = failureThreshold;
        this.recoveryWindowSeconds = recoveryWindowSeconds;
    }

    /** Default constructor: threshold=5, recovery=30s */
    public ProviderCircuitBreaker(String providerName) {
        this(providerName, 5, 30);
    }

    // ──────────────────────────────────────────────────────────────

    /**
     * Kiểm tra xem có được phép gửi request không.
     *
     * @return true → được gửi | false → bị chặn (fail-fast)
     */
    public synchronized boolean allowRequest() {
        return switch (state) {
            case CLOSED    -> true;

            case OPEN -> {
                // Kiểm tra hết recovery window chưa
                if (openedAt != null && Instant.now().isAfter(openedAt.plusSeconds(recoveryWindowSeconds))) {
                    transitionTo(CircuitState.HALF_OPEN);
                    log.info("🔶 [CB-{}] OPEN → HALF_OPEN (recovery window {}s hết)", providerName, recoveryWindowSeconds);
                    yield true; // Cho 1 request thử
                }
                log.debug("🔴 [CB-{}] OPEN — Reject ngay lập tức (fail-fast)", providerName);
                yield false;
            }

            case HALF_OPEN -> true; // Cho 1 request thử
        };
    }

    /**
     * Gọi sau khi request THÀNH CÔNG.
     */
    public synchronized void recordSuccess() {
        successCount.incrementAndGet();

        if (state == CircuitState.HALF_OPEN) {
            transitionTo(CircuitState.CLOSED);
            failureCount.set(0);
            log.info("🟢 [CB-{}] HALF_OPEN → CLOSED (probe request thành công)", providerName);
        } else if (state == CircuitState.CLOSED) {
            // Reset failure count khi có success
            failureCount.set(0);
        }
    }

    /**
     * Gọi sau khi request THẤT BẠI.
     */
    public synchronized void recordFailure() {
        int failures = failureCount.incrementAndGet();

        if (state == CircuitState.HALF_OPEN) {
            // Probe thất bại → quay lại OPEN, reset timer
            transitionTo(CircuitState.OPEN);
            openedAt = Instant.now();
            log.warn("🔴 [CB-{}] HALF_OPEN → OPEN (probe request thất bại)", providerName);

        } else if (state == CircuitState.CLOSED && failures >= failureThreshold) {
            transitionTo(CircuitState.OPEN);
            openedAt = Instant.now();
            log.error("🔴 [CB-{}] CLOSED → OPEN ({} lỗi liên tiếp, threshold={})",
                    providerName, failures, failureThreshold);
        }
    }

    public CircuitState getState()   { return state; }
    public int getFailureCount()     { return failureCount.get(); }
    public String getProviderName()  { return providerName; }

    public java.util.Map<String, Object> toStats() {
        return java.util.Map.of(
            "provider",        providerName,
            "state",           state.name(),
            "failures",        failureCount.get(),
            "successes",       successCount.get(),
            "threshold",       failureThreshold,
            "openedAt",        openedAt != null ? openedAt.toString() : "-",
            "recoverySeconds", recoveryWindowSeconds
        );
    }

    private void transitionTo(CircuitState newState) {
        log.debug("⚙️  [CB-{}] {} → {}", providerName, state, newState);
        this.state = newState;
    }
}
