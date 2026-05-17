package com.example.execrise2.ai_orchestrator.guardrails;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * [Phase 2.2] ContentGuardrailService — Nâng cấp lớp lá chắn.
 *
 * Fix 2.2 — 3 cải tiến so với phiên bản cũ:
 *
 * 1. NORMALIZE trước khi check:
 *    - NFD normalization → loại dấu Unicode đặc biệt
 *    - Leet-speak: 4→a, 3→e, 0→o, 1→i
 *    → Bypass "h4ck", "нack" (Cyrillic) bị chặn
 *
 * 2. REGEX PATTERNS thay vì chỉ exact string:
 *    - Pattern-based matching cho lớp bảo vệ rộng hơn
 *    - Phân loại WARN vs BLOCK (severity)
 *
 * 3. RATE LIMITING per-user:
 *    - Đếm WARN trong 60s
 *    - Nếu > 3 WARN → tự động BLOCK user đó 5 phút
 *    - Dùng ConcurrentHashMap (thread-safe)
 */
@Service
@Slf4j
public class ContentGuardrailService {

    // ─── BLOCK patterns (reject ngay) ─────────────────────────────
    private static final List<Pattern> BLOCK_PATTERNS = List.of(
        Pattern.compile("(ignore|bo qua).*(previous|instruct|prompt)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("bỏ qua.*chỉ dẫn"),
        Pattern.compile("lam sao.*che tao.*vu khi"),
        Pattern.compile("hack|crack|exploit|bypass.*security", Pattern.CASE_INSENSITIVE),
        Pattern.compile("jailbreak|dan.*mode|developer.*mode", Pattern.CASE_INSENSITIVE),
        Pattern.compile("forget.*instructions|pretend.*no.*rules", Pattern.CASE_INSENSITIVE),
        Pattern.compile("you are now|từ bây giờ bạn là", Pattern.CASE_INSENSITIVE)
    );

    // ─── WARN patterns (log nhưng không block ngay) ───────────────
    private static final List<Pattern> WARN_PATTERNS = List.of(
        Pattern.compile("password|mật khẩu|api.?key", Pattern.CASE_INSENSITIVE),
        Pattern.compile("token|secret|credential", Pattern.CASE_INSENSITIVE),
        Pattern.compile("sql.*inject|drop.*table|select.*from", Pattern.CASE_INSENSITIVE)
    );

    // ─── Rate limiting per-user ────────────────────────────────────
    private static final int  WARN_THRESHOLD_PER_WINDOW = 3;    // 3 WARN trong 60s → auto-block
    private static final long WARN_WINDOW_SECONDS        = 60L;
    private static final long USER_BLOCK_SECONDS         = 300L; // 5 phút

    private final Map<String, UserState> userStates = new ConcurrentHashMap<>();

    // ──────────────────────────────────────────────────────────────

    /**
     * Validate message — throw SecurityException nếu vi phạm.
     *
     * @param message Tin nhắn của user
     * @param userId  ID user (dùng cho rate limiting; dùng "anonymous" nếu không có)
     */
    public boolean validateMessage(String message, String userId) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("Tin nhắn không được để trống!");
        }

        // 1. Kiểm tra user có đang bị block không
        checkUserBlocked(userId);

        // 2. Normalize: NFD + leet-speak
        String normalized = normalize(message);

        // 3. Kiểm tra BLOCK patterns
        for (Pattern p : BLOCK_PATTERNS) {
            if (p.matcher(normalized).find()) {
                log.error("🚨 [GUARDRAIL-BLOCK] userId={} | pattern='{}' | input='{}'",
                        userId, p.pattern(), message.substring(0, Math.min(50, message.length())));
                throw new SecurityException("Yêu cầu bị từ chối: vi phạm chính sách nội dung.");
            }
        }

        // 4. Kiểm tra WARN patterns + rate limiting
        for (Pattern p : WARN_PATTERNS) {
            if (p.matcher(normalized).find()) {
                log.warn("⚠️  [GUARDRAIL-WARN] userId={} | pattern='{}'", userId, p.pattern());
                int warnCount = recordWarn(userId);
                if (warnCount > WARN_THRESHOLD_PER_WINDOW) {
                    blockUser(userId);
                    throw new SecurityException("Quá nhiều yêu cầu đáng ngờ. Tài khoản tạm thời bị hạn chế 5 phút.");
                }
            }
        }

        return true;
    }

    /** Backward compatible — không có userId */
    public boolean validateMessage(String message) {
        return validateMessage(message, "anonymous");
    }

    // ──────────────────────────────────────────────────────────────
    //  HELPERS
    // ──────────────────────────────────────────────────────────────

    /**
     * Normalize input:
     *   1. NFD → loại dấu Unicode đặc biệt (Cyrillic lookalike, diacritic)
     *   2. Leet-speak: 4→a, 3→e, 0→o, 1→i
     *   3. Lowercase
     */
    String normalize(String input) {
        // NFD normalization
        String nfd = Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");

        // Leet-speak
        return nfd
                .replace("4", "a")
                .replace("3", "e")
                .replace("0", "o")
                .replace("1", "i")
                .toLowerCase();
    }

    private void checkUserBlocked(String userId) {
        UserState state = userStates.get(userId);
        if (state != null && state.blockedUntil != null && Instant.now().isBefore(state.blockedUntil)) {
            long remaining = state.blockedUntil.getEpochSecond() - Instant.now().getEpochSecond();
            throw new SecurityException("Tài khoản tạm thời bị hạn chế. Thử lại sau " + remaining + " giây.");
        }
    }

    private int recordWarn(String userId) {
        UserState state = userStates.computeIfAbsent(userId, k -> new UserState());
        Instant windowStart = Instant.now().minusSeconds(WARN_WINDOW_SECONDS);

        // Reset nếu window cũ hết hạn
        if (state.windowStart.isBefore(windowStart)) {
            state.warnCount.set(0);
            state.windowStart = Instant.now();
        }

        return state.warnCount.incrementAndGet();
    }

    private void blockUser(String userId) {
        UserState state = userStates.computeIfAbsent(userId, k -> new UserState());
        state.blockedUntil = Instant.now().plusSeconds(USER_BLOCK_SECONDS);
        log.error("🔒 [GUARDRAIL] User '{}' bị BLOCK {} giây (quá nhiều WARN)", userId, USER_BLOCK_SECONDS);
    }

    // ──────────────────────────────────────────────────────────────

    static class UserState {
        final AtomicInteger warnCount = new AtomicInteger(0);
        volatile Instant windowStart  = Instant.now();
        volatile Instant blockedUntil = null;
    }
}
