package com.example.execrise2.ai_orchestrator.pool;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * [Phase 3.2] GeminiKeyPool — Pool API key cho Gemini (đơn giản hơn GroqKeyPool).
 *
 * Khác với Groq:
 *   - Không có EXHAUSTED state (Gemini chỉ có RPM limit, không có daily hard limit)
 *   - Chỉ có ACTIVE và COOLING
 *   - Cooling ngắn hơn: 30s (default)
 *
 * Cấu hình:
 *   gemini.api-keys=key1,key2
 *   gemini.cooling-seconds=30
 */
@Component
@Slf4j
public class GeminiKeyPool {

    @Value("${gemini.api-keys:${gemini.api-key:NOT_SET}}")
    private String rawKeys;

    @Value("${gemini.cooling-seconds:30}")
    private long coolingSeconds;

    private final List<KeyEntry> pool = new ArrayList<>();

    @PostConstruct
    public void init() {
        if ("NOT_SET".equals(rawKeys) || rawKeys == null || rawKeys.isBlank()) {
            log.warn("⚠️  [GeminiPool] gemini.api-keys chưa cấu hình.");
            return;
        }

        for (String key : rawKeys.split(",")) {
            String trimmed = key.trim();
            if (!trimmed.isBlank()) {
                pool.add(new KeyEntry(trimmed));
            }
        }

        log.info("✅ [GeminiPool] Đã nạp {} key.", pool.size());
    }

    public synchronized String nextKey() {
        if (pool.isEmpty()) return null;

        return pool.stream()
                .filter(e -> e.status == KeyStatus.ACTIVE)
                .min(Comparator.comparingInt(e -> e.useCount.get()))
                .map(e -> { e.useCount.incrementAndGet(); return e.apiKey; })
                .orElse(null); // null → GeminiAdapter dùng mock fallback
    }

    public synchronized void markRateLimited(String apiKey) {
        pool.stream()
            .filter(e -> e.apiKey.equals(apiKey))
            .findFirst()
            .ifPresent(e -> {
                e.status = KeyStatus.COOLING;
                e.coolingUntil = Instant.now().plusSeconds(coolingSeconds);
                log.warn("🌡️  [GeminiPool] Key {}...{} → COOLING {}s", e.maskedKey(), e.suffix, coolingSeconds);
            });
    }

    public boolean isConfigured() { return !pool.isEmpty(); }

    public boolean isHealthy() {
        return pool.stream().anyMatch(e -> e.status == KeyStatus.ACTIVE);
    }

    public List<java.util.Map<String, Object>> getStats() {
        return pool.stream()
                .map(e -> java.util.Map.<String, Object>of(
                    "key",          e.maskedKey() + "..." + e.suffix,
                    "status",       e.status.name(),
                    "useCount",     e.useCount.get(),
                    "coolingUntil", e.coolingUntil != null ? e.coolingUntil.toString() : "-"
                ))
                .toList();
    }

    @Scheduled(fixedDelay = 15_000)
    public synchronized void recoverCoolingKeys() {
        Instant now = Instant.now();
        pool.stream()
            .filter(e -> e.status == KeyStatus.COOLING && now.isAfter(e.coolingUntil))
            .forEach(e -> {
                e.status = KeyStatus.ACTIVE;
                e.coolingUntil = null;
                log.info("✅ [GeminiPool] Key {}...{} → ACTIVE", e.maskedKey(), e.suffix);
            });
    }

    /** Reset thủ công tất cả key về ACTIVE (dùng khi sang giờ mới). */
    public synchronized void resetAllToActive() {
        pool.forEach(e -> {
            e.status = KeyStatus.ACTIVE;
            e.coolingUntil = null;
        });
        log.info("🔄 [GeminiPool] Đã reset toàn bộ {} key về ACTIVE.", pool.size());
    }

    enum KeyStatus { ACTIVE, COOLING }

    static class KeyEntry {
        final String apiKey;
        final String suffix;
        volatile KeyStatus status = KeyStatus.ACTIVE;
        final AtomicInteger useCount = new AtomicInteger(0);
        volatile Instant coolingUntil = null;

        KeyEntry(String apiKey) {
            this.apiKey  = apiKey;
            this.suffix  = apiKey.length() >= 4 ? apiKey.substring(apiKey.length() - 4) : "????";
        }

        String maskedKey() {
            return apiKey.length() >= 6 ? apiKey.substring(0, 6) : "??????";
        }
    }
}
