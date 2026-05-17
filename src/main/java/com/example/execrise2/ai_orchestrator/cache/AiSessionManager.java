package com.example.execrise2.ai_orchestrator.cache;

import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * [Trụ cột 3] KV-CACHE AFFINITY & STICKY SESSIONS
 * Giả lập Redis: Ghi nhớ User nào đang dùng API Key nào để gọi lại chính Key đó ở câu hỏi tiếp theo,
 * giúp AI không phải tốn thời gian "đọc lại" (Pre-fill) lịch sử trò chuyện.
 */
@Service
public class AiSessionManager {

    // Giả lập Redis bằng ConcurrentHashMap (Thread-safe)
    // Key: UserId (hoặc SessionId), Value: ProviderName (VD: "GROQ")
    private final Map<String, String> userSessionCache = new ConcurrentHashMap<>();

    /**
     * Ghi nhớ phiên làm việc của User
     */
    public void saveUserProvider(String userId, String providerName) {
        userSessionCache.put(userId, providerName);
        System.out.println("💾 [SESSION] Đã ghi nhớ User " + userId + " đang dùng " + providerName);
    }

    /**
     * Lấy lại Provider cũ của User (nếu có)
     */
    public String getPreviousProvider(String userId) {
        return userSessionCache.get(userId);
    }

    /**
     * Xóa phiên (Ví dụ khi User bấm nút "Xóa lịch sử trò chuyện")
     */
    public void clearSession(String userId) {
        userSessionCache.remove(userId);
    }
}
