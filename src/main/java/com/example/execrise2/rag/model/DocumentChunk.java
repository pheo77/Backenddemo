package com.example.execrise2.rag.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * [LAYER 1] ENTITY: DocumentChunk
 * Lưu trữ từng mảnh nội dung đã được cắt nhỏ + vector embedding.
 * Cột embedding dùng PGVector (vector(1536)).
 */
@Entity
@Table(name = "document_chunks", indexes = {
    @Index(name = "idx_chunks_doctype", columnList = "doc_type"),
    @Index(name = "idx_chunks_lang",    columnList = "language")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Nội dung văn bản của chunk */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    /**
     * Vector embedding (1536 chiều — khớp với text-embedding-3-small / Gemini).
     * NOTE: Annotation @Column(columnDefinition = "vector(1536)") chỉ hoạt động
     *       khi PostgreSQL có pgvector extension. Khi dùng H2 (test), bỏ qua.
     */
    @Column(name = "embedding", columnDefinition = "float[]")
    private float[] embedding;

    // ──────────────────────────────────────────────────────────────
    //  METADATA — BẮT BUỘC để lọc trước khi tìm kiếm (Pre-filter)
    // ──────────────────────────────────────────────────────────────

    /** URL / đường dẫn tài liệu gốc */
    @Column(name = "source_url", length = 1000)
    private String sourceUrl;

    /** Loại nội dung: grammar, vocabulary, culture, kanji... */
    @Column(name = "doc_type", length = 100)
    private String docType;

    /** Ngôn ngữ: vi, ja, en */
    @Column(name = "language", length = 10)
    private String language;

    /** Số trang trong tài liệu gốc (nếu có) */
    @Column(name = "page_number")
    private int pageNumber;

    /** Version của tài liệu để quản lý nâng cấp */
    @Column(name = "version", length = 50)
    private String version;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    /** Tags tự do để lọc nhanh thêm */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "document_chunk_tags",
                     joinColumns = @JoinColumn(name = "chunk_id"))
    @Column(name = "tag")
    private List<String> tags;

    /**
     * Permission control — ai được đọc chunk này
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "permission_level", length = 20)
    @Builder.Default
    private PermissionLevel permissionLevel = PermissionLevel.PUBLIC;

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public enum PermissionLevel {
        PUBLIC,        // Tất cả user
        TEACHER_ONLY,  // Chỉ giáo viên
        ADMIN_ONLY     // Chỉ admin
    }
}
