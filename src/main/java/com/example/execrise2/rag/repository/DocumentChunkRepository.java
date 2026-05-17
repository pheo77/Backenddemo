package com.example.execrise2.rag.repository;

import com.example.execrise2.rag.model.DocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * [LAYER 2] REPOSITORY: Truy vấn DocumentChunk từ PostgreSQL + PGVector.
 *
 * Toán tử `<=>` là Cosine Distance của pgvector extension.
 * Yêu cầu: PostgreSQL có extension `vector` đã được bật.
 *
 * ⚠️  Các @Query native dưới đây CHỈ chạy với PostgreSQL + pgvector.
 *     Khi chạy test với H2 in-memory, cần mock hoặc dùng @Profile.
 */
@Repository
public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, UUID> {

    // ──────────────────────────────────────────────────────────────
    //  VECTOR SEARCH — Cosine Similarity (PGVector)
    // ──────────────────────────────────────────────────────────────

    /**
     * Tìm Top-K chunk gần nhất về mặt ngữ nghĩa, có lọc theo docType & language.
     * Dùng Cosine Distance (<=>), kết quả nhỏ hơn = gần hơn.
     */
    @Query(value = """
        SELECT * FROM document_chunks
        WHERE doc_type = :docType
          AND language = :lang
        ORDER BY embedding <=> cast(:queryVector AS vector)
        LIMIT :k
        """, nativeQuery = true)
    List<DocumentChunk> findSimilar(
        @Param("queryVector") float[] queryVector,
        @Param("docType") String docType,
        @Param("lang") String lang,
        @Param("k") int k
    );

    /**
     * Tìm kiếm Vector với Permission Control.
     * allowedLevels là danh sách: ['PUBLIC', 'TEACHER_ONLY']
     */
    @Query(value = """
        SELECT * FROM document_chunks
        WHERE doc_type = :docType
          AND language = :lang
          AND permission_level IN (:allowedLevels)
        ORDER BY embedding <=> cast(:queryVector AS vector)
        LIMIT :k
        """, nativeQuery = true)
    List<DocumentChunk> findSimilarWithPermission(
        @Param("queryVector") float[] queryVector,
        @Param("docType") String docType,
        @Param("lang") String lang,
        @Param("allowedLevels") List<String> allowedLevels,
        @Param("k") int k
    );

    // ──────────────────────────────────────────────────────────────
    //  KEYWORD SEARCH — JPQL (Backup khi không có Hibernate Search)
    // ──────────────────────────────────────────────────────────────

    /**
     * Tìm kiếm keyword đơn giản bằng LIKE (fallback khi không có Lucene).
     * Lưu ý: LOWER() gây full table scan — chỉ dùng khi data nhỏ < 100K rows.
     */
    @Query("""
        SELECT c FROM DocumentChunk c
        WHERE LOWER(c.content) LIKE LOWER(CONCAT('%', :keyword, '%'))
          AND c.docType = :docType
        ORDER BY c.createdAt DESC
        """)
    List<DocumentChunk> findByKeyword(
        @Param("keyword") String keyword,
        @Param("docType") String docType
    );

    // ──────────────────────────────────────────────────────────────
    //  MANAGEMENT
    // ──────────────────────────────────────────────────────────────

    List<DocumentChunk> findBySourceUrl(String sourceUrl);

    void deleteBySourceUrl(String sourceUrl);

    long countByDocType(String docType);
}
