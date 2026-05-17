package com.example.execrise2.rag.ingestion;

import com.example.execrise2.rag.model.DocumentChunk;
import com.example.execrise2.rag.model.DocumentMetadata;
import com.example.execrise2.rag.repository.DocumentChunkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.IntStream;

/**
 * [LAYER 1] INGESTION PIPELINE — Orchestrator chính của quá trình nhập liệu.
 *
 * Luồng: Upload → Load → Clean → Chunk → Embed → Index → Event
 *
 * Sử dụng @Async event để không block HTTP request khi upload file lớn.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentIngestionPipeline {

    private final SemanticChunker chunker;
    private final EmbeddingClientFacade embeddingClient;
    private final DocumentChunkRepository repository;
    private final ApplicationEventPublisher eventPublisher;

    // ──────────────────────────────────────────────────────────────
    //  PUBLIC API
    // ──────────────────────────────────────────────────────────────

    /**
     * Ingest từ MultipartFile (dùng cho REST endpoint /api/rag/ingest).
     * Trả về ngay, việc index chạy ngầm qua Spring Event.
     */
    public void ingestAsync(MultipartFile file, DocumentMetadata meta) {
        eventPublisher.publishEvent(new DocumentUploadedEvent(file, meta));
        log.info("📤 [INGESTION] File '{}' đã được nhận. Đang index ngầm...", file.getOriginalFilename());
    }

    /**
     * Ingest trực tiếp (synchronous) — dùng khi cần đảm bảo chunk sẵn sàng ngay.
     */
    @Transactional
    public int ingestSync(String rawText, DocumentMetadata meta) {
        log.info("🔄 [INGESTION] Bắt đầu xử lý {} ký tự từ '{}'", rawText.length(), meta.sourceUrl());

        // Bước 1: Làm sạch văn bản
        String cleaned = cleanText(rawText);

        // Bước 2: Cắt nhỏ thành chunk theo ranh giới ngữ nghĩa
        List<String> chunks = chunker.chunk(cleaned);
        log.info("   ✂️  Cắt thành {} chunk", chunks.size());

        // Bước 3: Embed tất cả cùng lúc (Batch) — tiết kiệm API call
        List<float[]> embeddings = embeddingClient.embedBatch(chunks);
        log.info("   🔢 Đã embed xong {} vector", embeddings.size());

        // Bước 4: Build Entity và lưu vào DB
        List<DocumentChunk> entities = IntStream.range(0, chunks.size())
            .mapToObj(i -> buildChunk(chunks.get(i), embeddings.get(i), meta, i))
            .toList();

        repository.saveAll(entities);
        log.info("✅ [INGESTION] Đã index {} chunk từ '{}'", entities.size(), meta.sourceUrl());

        return entities.size();
    }

    /**
     * Xóa toàn bộ chunk của một tài liệu (để re-index khi tài liệu thay đổi).
     */
    @Transactional
    public void reindex(MultipartFile file, DocumentMetadata meta) {
        repository.deleteBySourceUrl(meta.sourceUrl());
        log.info("🗑️  [INGESTION] Đã xóa chunk cũ của '{}'", meta.sourceUrl());
        ingestAsync(file, meta);
    }

    // ──────────────────────────────────────────────────────────────
    //  PRIVATE HELPERS
    // ──────────────────────────────────────────────────────────────

    private String loadText(MultipartFile file) {
        try {
            // Đọc file thuần text (UTF-8)
            // TODO: Thay bằng Apache Tika để hỗ trợ PDF, DOCX, HTML
            //   Tika tika = new Tika();
            //   return tika.parseToString(file.getInputStream());
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Không đọc được file: " + file.getOriginalFilename(), e);
        }
    }

    private String cleanText(String raw) {
        return raw
            .replaceAll("\\s+", " ")           // Gộp whitespace
            .replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "") // Xóa control chars
            .trim();
    }

    private DocumentChunk buildChunk(String content, float[] embedding,
                                     DocumentMetadata meta, int index) {
        return DocumentChunk.builder()
            .content(content)
            .embedding(embedding)
            .sourceUrl(meta.sourceUrl())
            .docType(meta.docType())
            .language(meta.language())
            .version(meta.version())
            .pageNumber(index)
            .permissionLevel(DocumentChunk.PermissionLevel.valueOf(
                meta.permissionLevel() != null ? meta.permissionLevel() : "PUBLIC"
            ))
            .build();
    }

    // ──────────────────────────────────────────────────────────────
    //  INNER EVENT (Spring Application Event)
    // ──────────────────────────────────────────────────────────────

    /**
     * Event được publish khi file mới upload thành công.
     * DocumentIndexingListener sẽ bắt event này và xử lý ngầm (@Async).
     */
    public record DocumentUploadedEvent(MultipartFile file, DocumentMetadata meta) {}
}
