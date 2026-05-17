package com.example.execrise2.ai_orchestrator.adapter;

import com.example.execrise2.ai_orchestrator.pool.GroqKeyPool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * [REAL] Groq Adapter — Reactive WebClient + Key Pool Rotation.
 *
 * Fix 1.2: Xóa .block() bên trong supplyAsync().
 *   Cũ: CompletableFuture.supplyAsync(() -> { ... .block() ... })
 *         → Chặn thread pool, triệt tiêu lợi thế async.
 *   Mới: Mono<String> reactive pipeline → .toFuture()
 *         → Không chặn thread, hoàn toàn non-blocking.
 *
 * Retry logic: Mono.retryWhen(Retry.max(3).filter(is429))
 *   → Khi 429: markRateLimited(currentKey) → nextKey() → retry với key mới.
 */
@Component
@Slf4j
public class GroqAdapter implements AiProviderAdapter {

    private static final String GROQ_BASE_URL = "https://api.groq.com/openai/v1";
    private static final int    MAX_RETRIES    = 3;

    @Value("${groq.model:llama-3.3-70b-versatile}")
    private String model;

    private final GroqKeyPool keyPool;
    private final WebClient   webClient;

    public GroqAdapter(GroqKeyPool keyPool, WebClient.Builder webClientBuilder) {
        this.keyPool   = keyPool;
        this.webClient = webClientBuilder
                .baseUrl(GROQ_BASE_URL)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public String getProviderName() { return "GROQ"; }

    /**
     * Fully reactive — không dùng .block() hay supplyAsync().
     * Pipeline: nextKey() → POST /chat/completions → parse → retry nếu 429
     */
    @Override
    public CompletableFuture<String> generateResponseAsync(String systemPrompt, String userMessage) {
        if (!keyPool.isConfigured()) {
            log.warn("⚠️  [Groq] Pool chưa cấu hình → MOCK.");
            return CompletableFuture.completedFuture("[MOCK] Groq chưa có API key: " + userMessage);
        }

        // AtomicRef để track key đang dùng qua các lần retry
        AtomicReference<String> currentKey = new AtomicReference<>(safeNextKey());

        return buildMono(systemPrompt, userMessage, currentKey)
                // Retry khi gặp 429 — tối đa MAX_RETRIES lần
                .retryWhen(Retry.max(MAX_RETRIES)
                        .filter(e -> e instanceof WebClientResponseException ex
                                && ex.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS)
                        .doBeforeRetry(signal -> {
                            String failedKey = currentKey.get();
                            boolean isDaily  = signal.failure().getMessage() != null
                                    && signal.failure().getMessage().contains("daily");
                            if (isDaily) {
                                keyPool.markExhausted(failedKey);
                            } else {
                                keyPool.markRateLimited(failedKey);
                            }
                            currentKey.set(safeNextKey());
                            log.warn("🔄 [Groq] 429 → rotate key → retry #{}", signal.totalRetries() + 1);
                        })
                )
                // Pool cạn sau tất cả retries → throw PoolExhaustedException
                .onErrorMap(WebClientResponseException.class, e ->
                        e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS
                                ? new GroqKeyPool.PoolExhaustedException("Rate limit sau " + MAX_RETRIES + " lần retry")
                                : e
                )
                .onErrorResume(GroqKeyPool.PoolExhaustedException.class, Mono::error)
                .onErrorReturn("❌ Groq lỗi không xác định. Vui lòng thử lại.")
                .toFuture();
    }

    /** Xây Mono cho 1 lần gọi API với key hiện tại */
    private Mono<String> buildMono(String systemPrompt, String userMessage,
                                   AtomicReference<String> keyRef) {
        return Mono.defer(() -> {
            String apiKey = keyRef.get();
            if (apiKey == null) {
                return Mono.error(new GroqKeyPool.PoolExhaustedException("Không còn key ACTIVE"));
            }

            log.info("🚀 [Groq] POST /chat/completions | model={} | key={}...",
                    model, apiKey.substring(0, Math.min(8, apiKey.length())));

            Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user",   "content", userMessage)
                ),
                "temperature", 0.3,
                "max_tokens",  1024
            );

            return webClient.post()
                    .uri("/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .map(this::parseResponse)
                    .timeout(Duration.ofSeconds(10))
                    .doOnSuccess(r -> log.info("✅ [Groq] OK ({} ký tự)", r.length()));
        });
    }

    @SuppressWarnings("unchecked")
    private String parseResponse(Map<?, ?> response) {
        try {
            List<?> choices = (List<?>) response.get("choices");
            Map<?, ?> msg   = (Map<?, ?>) ((Map<?, ?>) choices.get(0)).get("message");
            return (String) msg.get("content");
        } catch (Exception e) {
            log.warn("⚠️  [Groq] Parse lỗi: {}", e.getMessage());
            return "Groq trả về response không hợp lệ.";
        }
    }

    private String safeNextKey() {
        try { return keyPool.nextKey(); }
        catch (GroqKeyPool.PoolExhaustedException e) { return null; }
    }

    @Override
    public boolean isHealthy() {
        return keyPool.isConfigured() && !keyPool.isPoolExhausted();
    }

    public List<Map<String, Object>> getPoolStats() { return keyPool.getStats(); }
    public void resetPool() { keyPool.resetAll(); }
}
