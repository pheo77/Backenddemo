package com.example.execrise2.rag.ingestion;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * [LAYER 1] EVENT LISTENER: Tự động index tài liệu khi nhận được DocumentUploadedEvent.
 *
 * Chạy trong luồng ngầm (@Async) → HTTP request không phải chờ indexing hoàn tất.
 * Yêu cầu: @EnableAsync trong class cấu hình hoặc Application.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DocumentIndexingListener {

    private final DocumentIngestionPipeline pipeline;

    /**
     * Lắng nghe DocumentUploadedEvent → xử lý index ngầm.
     *
     * @Async → chạy trên thread pool riêng (Virtual Thread executor nếu được cấu hình)
     */
    @EventListener
    @Async
    public void onDocumentUploaded(DocumentIngestionPipeline.DocumentUploadedEvent event) {
        var file = event.file();
        var meta = event.meta();

        log.info("📥 [INDEXING] Bắt đầu index '{}' (docType={}, lang={})",
            file.getOriginalFilename(), meta.docType(), meta.language());

        try {
            String text = new String(file.getBytes(), StandardCharsets.UTF_8);
            int count = pipeline.ingestSync(text, meta);
            log.info("✅ [INDEXING] Hoàn tất '{}' → {} chunk", file.getOriginalFilename(), count);
        } catch (Exception e) {
            log.error("❌ [INDEXING] Lỗi khi index '{}': {}", file.getOriginalFilename(), e.getMessage(), e);
        }
    }
}
