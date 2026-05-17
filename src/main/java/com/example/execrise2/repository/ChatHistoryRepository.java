package com.example.execrise2.repository;

import com.example.execrise2.entity.ChatHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * [REPOSITORY] ChatHistoryRepository
 * Truy xuất lịch sử hội thoại của người dùng với Chatbot.
 */
@Repository
public interface ChatHistoryRepository extends JpaRepository<ChatHistory, UUID> {

    /**
     * Lấy lịch sử chat của một người dùng, mới nhất trước.
     */
    List<ChatHistory> findByUserIdOrderByCreatedAtDesc(Long userId);

    /**
     * Lấy N chat gần nhất của người dùng.
     */
    @Query("""
        SELECT c FROM ChatHistory c
        WHERE c.user.id = :userId
        ORDER BY c.createdAt DESC
        LIMIT :limit
        """)
    List<ChatHistory> findRecentByUserId(
        @Param("userId") Long userId,
        @Param("limit") int limit
    );

    /**
     * Thống kê số câu hỏi theo từng AI provider.
     */
    @Query("""
        SELECT c.aiProvider, COUNT(c)
        FROM ChatHistory c
        GROUP BY c.aiProvider
        """)
    List<Object[]> countByProvider();

    /** Tổng số câu hỏi đã xử lý */
    long count();
}
