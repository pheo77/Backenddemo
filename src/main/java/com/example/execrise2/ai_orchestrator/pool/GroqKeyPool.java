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
import java.util.concurrent.atomic.AtomicLong;

/**
 * [POOL] GroqKeyPool — Quản lý nhiều Groq API key theo chiến lược Least-Used + Health-Aware.
 *
 * Chiến lược chọn key:
 *   1. Lọc tất cả key đang ACTIVE (không COOLING, không EXHAUSTED)
 *   2. Trong các key ACTIVE, chọn key có useCount thấp nhất (Least-Used)
 *   3. Nếu tất cả COOLING → chờ reset hoặc throw PoolExhaustedException
 *
 * Recovery tự động:
 *   - COOLING key tự reset về ACTIVE sau 60 giây (scheduler chạy mỗi 15s kiểm tra)
 *   - EXHAUSTED key phải reset thủ công qua /api/ai/pool-reset
 *
 * Cấu hình (application.properties):
 *   groq.api-keys=gsk_key1,gsk_key2,gsk_key3
 *   groq.cooling-seconds=60
 */
@Component
@Slf4j
public class GroqKeyPool {

    @Value("${groq.api-keys:NOT_SET}")
    private String rawKeys;

    @Value("${groq.cooling-seconds:60}")
    private long coolingSeconds;

    private final List<KeyEntry> pool = new ArrayList<>();

    // ──────────────────────────────────────────────────────────────
    //  INIT
    // ──────────────────────────────────────────────────────────────

    @PostConstruct
    public void init() {
        if ("NOT_SET".equals(rawKeys) || rawKeys == null || rawKeys.isBlank()) {
            log.warn("⚠️  [KeyPool] groq.api-keys chưa được cấu hình! Chatbot sẽ dùng MOCK mode.");
            return;
        }

        String[] keys = rawKeys.split(",");
        for (String key : keys) {
            String trimmed = key.trim();
            if (!trimmed.isBlank()) {
                pool.add(new KeyEntry(trimmed));
            }
        }

        log.info("✅ [KeyPool] Đã nạp {} Groq API key vào pool.", pool.size());
        pool.forEach(e -> log.info("   → Key: {}...{}", e.maskedKey(), e.status));
    }

    // ──────────────────────────────────────────────────────────────
    //  CORE: Chọn key tốt nhất
    // ──────────────────────────────────────────────────────────────

    /**
     * Lấy key Groq tốt nhất (ACTIVE + ít dùng nhất).
     *
     * @return API key string, hoặc null nếu pool rỗng / chưa cấu hình
     * @throws PoolExhaustedException nếu tất cả key đang COOLING/EXHAUSTED
     */
    public synchronized String nextKey() {
        if (pool.isEmpty()) {
            log.warn("⚠️  [KeyPool] Pool rỗng — chưa cấu hình key.");
            return null;
        }

        // Lọc key ACTIVE
        List<KeyEntry> activeKeys = pool.stream()
                .filter(e -> e.status == KeyStatus.ACTIVE)
                .toList();

        if (activeKeys.isEmpty()) {
            long coolingCount   = pool.stream().filter(e -> e.status == KeyStatus.COOLING).count();
            long exhaustedCount = pool.stream().filter(e -> e.status == KeyStatus.EXHAUSTED).count();
            log.warn("❌ [KeyPool] Tất cả key đều không dùng được! COOLING={} | EXHAUSTED={}",
                    coolingCount, exhaustedCount);
            throw new PoolExhaustedException(
                "Tất cả " + pool.size() + " Groq API key đều đang bị giới hạn. " +
                "COOLING=" + coolingCount + " | EXHAUSTED=" + exhaustedCount
            );
        }

        // Chọn key ít dùng nhất (Least-Used)
        KeyEntry best = activeKeys.stream()
                .min(Comparator.comparingLong(e -> e.useCount.get()))
                .orElseThrow();

        best.useCount.incrementAndGet();
        best.lastUsedAt.set(Instant.now().toEpochMilli());

        log.debug("🔑 [KeyPool] Chọn key {}...{} (useCount={})",
                best.maskedKey(), best.suffix, best.useCount.get());
        return best.apiKey;
    }

    // ──────────────────────────────────────────────────────────────
    //  MARK — Cập nhật trạng thái key
    // ──────────────────────────────────────────────────────────────

    /**
     * Đánh dấu key bị Rate Limit (HTTP 429).
     * Key sẽ tự động COOLING trong {@code coolingSeconds} giây rồi recover về ACTIVE.
     */
    public synchronized void markRateLimited(String apiKey) {
        pool.stream()
            .filter(e -> e.apiKey.equals(apiKey))
            .findFirst()
            .ifPresent(e -> {
                e.status = KeyStatus.COOLING;
                e.coolingUntil = Instant.now().plusSeconds(coolingSeconds);
                e.rateLimitCount.incrementAndGet();
                log.warn("🌡️  [KeyPool] Key {}...{} bị RATE LIMIT → COOLING {}s (tổng {} lần)",
                        e.maskedKey(), e.suffix, coolingSeconds, e.rateLimitCount.get());
            });
    }

    /**
     * Đánh dấu key hết quota ngày (HTTP 429 với message daily limit).
     * Phải reset thủ công.
     */
    public synchronized void markExhausted(String apiKey) {
        pool.stream()
            .filter(e -> e.apiKey.equals(apiKey))
            .findFirst()
            .ifPresent(e -> {
                e.status = KeyStatus.EXHAUSTED;
                log.error("💀 [KeyPool] Key {}...{} đã HẾT QUOTA ngày! Cần thêm key mới.",
                        e.maskedKey(), e.suffix);
            });
    }

    /**
     * Kiểm tra pool có còn key nào dùng được không.
     */
    public boolean isPoolExhausted() {
        return pool.isEmpty() || pool.stream().noneMatch(e -> e.status == KeyStatus.ACTIVE);
    }

    /**
     * Kiểm tra pool đã được cấu hình key chưa.
     */
    public boolean isConfigured() {
        return !pool.isEmpty();
    }

    // ──────────────────────────────────────────────────────────────
    //  SCHEDULER: Tự động recover key COOLING → ACTIVE
    // ──────────────────────────────────────────────────────────────

    /**
     * Chạy mỗi 15 giây: kiểm tra key nào hết thời gian cooling → recover về ACTIVE.
     */
    @Scheduled(fixedDelay = 15_000)
    public synchronized void recoverCoolingKeys() {
        Instant now = Instant.now();
        pool.stream()
            .filter(e -> e.status == KeyStatus.COOLING && now.isAfter(e.coolingUntil))
            .forEach(e -> {
                e.status = KeyStatus.ACTIVE;
                e.coolingUntil = null;
                log.info("✅ [KeyPool] Key {}...{} đã hết cooling → ACTIVE lại.", e.maskedKey(), e.suffix);
            });
    }

    // ──────────────────────────────────────────────────────────────
    //  STATS — Trạng thái pool để hiện ở Admin endpoint
    // ──────────────────────────────────────────────────────────────

    /**
     * Thống kê trạng thái toàn bộ pool (không lộ key thật).
     */
    public List<java.util.Map<String, Object>> getStats() {
        return pool.stream()
                .map(e -> java.util.Map.<String, Object>of(
                    "key",          e.maskedKey() + "..." + e.suffix,
                    "status",       e.status.name(),
                    "useCount",     e.useCount.get(),
                    "rateLimits",   e.rateLimitCount.get(),
                    "coolingUntil", e.coolingUntil != null ? e.coolingUntil.toString() : "-"
                ))
                .toList();
    }

    /**
     * Reset thủ công tất cả EXHAUSTED key về ACTIVE (dùng khi sang ngày mới).
     */
    public synchronized void resetAll() {
        pool.forEach(e -> {
            e.status = KeyStatus.ACTIVE;
            e.coolingUntil = null;
        });
        log.info("🔄 [KeyPool] Đã reset toàn bộ {} key về ACTIVE.", pool.size());
    }

    // ──────────────────────────────────────────────────────────────
    //  INNER CLASSES
    // ──────────────────────────────────────────────────────────────

    public enum KeyStatus {
        ACTIVE,     // Đang dùng được
        COOLING,    // Bị 429, chờ hết thời gian cooling
        EXHAUSTED   // Hết quota ngày — phải reset thủ công
    }

    /**
     * Metadata của mỗi API key trong pool.
     */
    static class KeyEntry {
        final String apiKey;
        final String suffix;               // 4 ký tự cuối để log (không lộ full key)
        volatile KeyStatus status = KeyStatus.ACTIVE;
        final AtomicInteger useCount       = new AtomicInteger(0);
        final AtomicInteger rateLimitCount = new AtomicInteger(0);
        final AtomicLong lastUsedAt        = new AtomicLong(0);
        volatile Instant coolingUntil      = null;

        KeyEntry(String apiKey) {
            this.apiKey  = apiKey;
            this.suffix  = apiKey.length() >= 4
                ? apiKey.substring(apiKey.length() - 4)
                : "????";
        }

        /** Hiển thị 6 ký tự đầu, che phần còn lại */
        String maskedKey() {
            return apiKey.length() >= 6 ? apiKey.substring(0, 6) : "??????";
        }
    }

    /**
     * Ném ra khi tất cả key trong pool đều không dùng được.
     */
    public static class PoolExhaustedException extends RuntimeException {
        public PoolExhaustedException(String message) {
            super(message);
        }
    }
}
