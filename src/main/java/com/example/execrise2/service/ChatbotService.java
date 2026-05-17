package com.example.execrise2.service;

import com.example.execrise2.ai_orchestrator.adapter.GroqAdapter;
import com.example.execrise2.entity.ChatHistory;
import com.example.execrise2.entity.User;
import com.example.execrise2.rag.generation.HybridRagOrchestrator;
import com.example.execrise2.rag.model.RagRequest;
import com.example.execrise2.rag.model.RagResponse;
import com.example.execrise2.rag.model.RetrievalOptions;
import com.example.execrise2.repository.ChatHistoryRepository;
import com.example.execrise2.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * [SERVICE] ChatbotService -> Chatbot Assistant Service
 *
 * Orchestrate toàn bộ luồng Chatbot:
 * 1. Nhận câu hỏi + userId
 * 2. Gọi HybridRagOrchestrator (RAG pipeline đầy đủ)
 * 3. Lưu kết quả vào ChatHistory (JPA)
 * 4. Trả về response
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatbotService {

    private final HybridRagOrchestrator ragOrchestrator;
    private final ChatHistoryRepository chatHistoryRepository;
    private final GroqAdapter groqAdapter;
    private final UserRepository userRepo;

    // docType dùng cho Danang Lắng Nghe
    private static final String DANANG_DOC_TYPE = "danang-policy";
    private static final String LANGUAGE     = "vi";

    /**
     * Hỏi chatbot và lưu lịch sử.
     *
     * @param userId ID người dùng (phải tồn tại trong DB)
     * @param question  Câu hỏi
     * @return Map chứa answer, citations, metadata
     */
    @Transactional
    public Map<String, Object> ask(Long userId, String question) {
        long start = System.currentTimeMillis();
        log.info("📨 [Chatbot] userId={} | question='{}'", userId, question);

        // 1. Load User (để lưu FK vào ChatHistory)
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy người dùng ID=" + userId));

        // 2. Gọi Hybrid RAG pipeline
        RetrievalOptions options = RetrievalOptions.defaults(DANANG_DOC_TYPE, LANGUAGE);
        RagRequest request = new RagRequest(question, options);
        RagResponse ragResponse = ragOrchestrator.query(request);

        long latencyMs = System.currentTimeMillis() - start;

        // 3. Lưu vào ChatHistory (JPA)
        ChatHistory history = ChatHistory.builder()
                .user(user)
                .question(question)
                .answer(ragResponse.answer())
                .docType(DANANG_DOC_TYPE)
                .aiProvider(groqAdapter.isHealthy() ? "GROQ" : "MOCK")
                .latencyMs(latencyMs)
                .build();

        chatHistoryRepository.save(history);
        log.info("✅ [Chatbot] Đã lưu ChatHistory id={} | latency={}ms",
                history.getId(), latencyMs);

        // 4. Trả về response
        return Map.of(
            "answer",      ragResponse.answer(),
            "citations",   ragResponse.citations(),
            "latencyMs",   latencyMs,
            "provider",    groqAdapter.isHealthy() ? "GROQ/llama-3.3-70b-versatile" : "MOCK",
            "userId",      userId,
            "chatId",      history.getId().toString()
        );
    }

    /**
     * Lấy lịch sử chat của người dùng (tối đa 20 câu gần nhất).
     */
    @Transactional(readOnly = true)
    public List<ChatHistory> getHistory(Long userId) {
        return chatHistoryRepository.findRecentByUserId(userId, 20);
    }

    /**
     * Thống kê tổng số câu hỏi.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getStats() {
        long totalChats = chatHistoryRepository.count();
        List<Object[]> byProvider = chatHistoryRepository.countByProvider();

        return Map.of(
            "totalChats", totalChats,
            "byProvider", byProvider.stream()
                .map(row -> Map.of("provider", row[0], "count", row[1]))
                .toList()
        );
    }
}
