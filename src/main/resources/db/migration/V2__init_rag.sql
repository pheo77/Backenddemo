-- =====================================================
-- Flyway Migration: V2__init_rag.sql
-- Tạo bảng document_chunks cho Hybrid RAG module
-- Yêu cầu: PostgreSQL với pgvector extension
-- =====================================================

-- BƯỚC 1: Bật pgvector extension (chạy 1 lần, idempotent)
CREATE EXTENSION IF NOT EXISTS vector;

-- BƯỚC 2: Tạo bảng chính
CREATE TABLE IF NOT EXISTS document_chunks (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    content          TEXT        NOT NULL,
    embedding        VECTOR(1536),          -- Khớp với text-embedding-3-small / Gemini
    source_url       VARCHAR(1000),
    doc_type         VARCHAR(100),          -- grammar, vocabulary, kanji, culture...
    language         VARCHAR(10),           -- vi, ja, en
    page_number      INTEGER     DEFAULT 0,
    version          VARCHAR(50) DEFAULT '1.0',
    permission_level VARCHAR(20) DEFAULT 'PUBLIC',  -- PUBLIC | TEACHER_ONLY | ADMIN_ONLY
    created_at       TIMESTAMPTZ DEFAULT NOW(),
    updated_at       TIMESTAMPTZ DEFAULT NOW()
);

-- BƯỚC 3: Bảng phụ cho tags (quan hệ 1-N)
CREATE TABLE IF NOT EXISTS document_chunk_tags (
    chunk_id UUID        NOT NULL REFERENCES document_chunks(id) ON DELETE CASCADE,
    tag      VARCHAR(100) NOT NULL
);

-- BƯỚC 4: Index HNSW cho Vector Search (Nhanh nhất — dùng cho production)
-- m=16: số neighbors mỗi node (trade-off: recall vs memory)
-- ef_construction=64: chất lượng graph lúc build (cao hơn = chậm hơn nhưng chính xác hơn)
CREATE INDEX IF NOT EXISTS idx_chunks_embedding_hnsw
    ON document_chunks
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

-- BƯỚC 5: Index thông thường cho Metadata Filtering (Pre-filter trước Vector Search)
CREATE INDEX IF NOT EXISTS idx_chunks_doctype  ON document_chunks(doc_type);
CREATE INDEX IF NOT EXISTS idx_chunks_lang     ON document_chunks(language);
CREATE INDEX IF NOT EXISTS idx_chunks_perm     ON document_chunks(permission_level);
CREATE INDEX IF NOT EXISTS idx_chunks_source   ON document_chunks(source_url);
CREATE INDEX IF NOT EXISTS idx_chunk_tags_id   ON document_chunk_tags(chunk_id);

-- BƯỚC 6: Auto-update updated_at (trigger)
CREATE OR REPLACE FUNCTION update_chunk_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE TRIGGER trigger_update_chunk_timestamp
BEFORE UPDATE ON document_chunks
FOR EACH ROW EXECUTE FUNCTION update_chunk_timestamp();

-- BƯỚC 7: Seed dữ liệu mẫu để test ngay (có thể xóa sau khi test)
-- Lưu ý: embedding là giá trị dummy (tất cả 0) — chỉ để test pipeline CRUD
INSERT INTO document_chunks (content, doc_type, language, source_url, version, permission_level)
VALUES
(
    'て形（te-form）là một dạng ngữ pháp cơ bản trong tiếng Nhật. ' ||
    'Nó được dùng để nối các hành động: 食べて、飲んで (ăn rồi uống). ' ||
    'Ngoài ra còn dùng để diễn đạt trạng thái: ドアが開いている (cửa đang mở). ' ||
    'Cách chia: động từ nhóm 1 đổi đuôi い/ち/り → って; び/み/に → んで; き → いて; ぎ → いで; し → して.',
    'grammar', 'ja',
    'sample://n4-grammar-te-form', '1.0', 'PUBLIC'
),
(
    '食べる (taberu) nghĩa là "ăn". Ví dụ: 私は毎日ご飯を食べます (Tôi ăn cơm mỗi ngày). ' ||
    'Động từ nhóm 2 (ichidan), て形: 食べて.',
    'vocabulary', 'ja',
    'sample://n5-vocab-taberu', '1.0', 'PUBLIC'
),
(
    'は (wa) là trợ từ chủ đề (topic marker) trong tiếng Nhật. ' ||
    'Phân biệt với が (ga) — trợ từ chủ ngữ (subject marker). ' ||
    'Ví dụ: 猫は魚を食べます (Mèo [chủ đề] ăn cá) vs 猫が魚を食べます (Chính mèo [chứ không phải thứ khác] ăn cá).',
    'grammar', 'ja',
    'sample://n5-grammar-wa-ga', '1.0', 'PUBLIC'
)
ON CONFLICT DO NOTHING;
