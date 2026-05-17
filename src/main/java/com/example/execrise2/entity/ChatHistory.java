package com.example.execrise2.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * [JPA ENTITY] ChatHistory — Lịch sử hội thoại của người dân với Chatbot.
 *
 * Quan hệ: Many-to-One với User (nhiều chat thuộc về 1 người dùng).
 * Table: chat_history
 */
@Entity
@Table(name = "chat_history", indexes = {
    @Index(name = "idx_chat_user_id", columnList = "user_id"),
    @Index(name = "idx_chat_created_at", columnList = "created_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Người dùng hỏi (FK → Users.id).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Câu hỏi đã gửi */
    @Column(name = "question", columnDefinition = "TEXT", nullable = false)
    private String question;

    /** Câu trả lời từ AI */
    @Column(name = "answer", columnDefinition = "TEXT")
    private String answer;

    /**
     * Loại tài liệu RAG dùng để trả lời.
     */
    @Column(name = "doc_type", length = 50)
    @Builder.Default
    private String docType = "danang-policy";

    /** Provider AI đã xử lý (GROQ, GEMINI, MOCK...) */
    @Column(name = "ai_provider", length = 30)
    @Builder.Default
    private String aiProvider = "GROQ";

    /** Thời gian xử lý (ms) */
    @Column(name = "latency_ms")
    private long latencyMs;

    /** Thời điểm tạo */
    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}
