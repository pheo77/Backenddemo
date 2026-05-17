package com.example.execrise2.rag.retrieval;

import com.example.execrise2.rag.ingestion.EmbeddingClientFacade;
import com.example.execrise2.rag.model.DocumentChunk;
import com.example.execrise2.rag.model.HybridRetrievalResult;
import com.example.execrise2.rag.model.RetrievalOptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.List;

/**
 * [LAYER 4] HYBRID RETRIEVER — Bắn cả 2 luồng Vector + BM25 SONG SONG.
 *
 * Dùng Java 21 Virtual Threads để chạy đồng thời không tốn tài nguyên.
 * CompletableFuture.allOf() chờ cả 2 hoàn thành rồi mới trả về.
 *
 * Lợi ích:
 * - Latency tổng = max(latency_vector, latency_bm25) thay vì tổng cộng
 * - Tận dụng I/O parallelism khi cả 2 đều query DB
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HybridRetriever {

    private final VectorRetriever vectorRetriever;
    private final BM25Retriever bm25Retriever;
    private final EmbeddingClientFacade embeddingClient;

    /**
     * Virtual Thread executor — không tốn tài nguyên khi chờ I/O.
     * Java 21: newVirtualThreadPerTaskExecutor() là lựa chọn tối ưu.
     */
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * Tìm kiếm kép song song: Vector (Dense) + BM25 (Sparse).
     *
     * @param queryText Câu hỏi đầu vào (đã qua QueryTransformer nếu cần)
     * @param options   Cấu hình lọc
     * @return HybridRetrievalResult chứa kết quả của cả 2 luồng
     */
    public HybridRetrievalResult retrieve(String queryText, RetrievalOptions options) {
        long start = System.currentTimeMillis();

        log.info("🚀 [HYBRID] Bắt đầu tìm kiếm kép song song...");

        // Embed câu hỏi một lần, dùng chung cho cả Vector search
        float[] queryVector = embeddingClient.embed(queryText);

        // ──────────────────────────────────────────────────────────
        //  Chạy 2 luồng SONG SONG qua Virtual Threads
        // ──────────────────────────────────────────────────────────
        CompletableFuture<List<DocumentChunk>> vectorFuture = CompletableFuture.supplyAsync(
            () -> {
                log.debug("   [T1] Vector search bắt đầu...");
                List<DocumentChunk> result = vectorRetriever.search(queryVector, options);
                log.debug("   [T1] Vector search xong: {} chunk", result.size());
                return result;
            },
            executor
        );

        CompletableFuture<List<DocumentChunk>> bm25Future = CompletableFuture.supplyAsync(
            () -> {
                log.debug("   [T2] BM25 search bắt đầu...");
                List<DocumentChunk> result = bm25Retriever.search(queryText, options.docType());
                log.debug("   [T2] BM25 search xong: {} chunk", result.size());
                return result;
            },
            executor
        );

        // Chờ cả 2 hoàn thành
        CompletableFuture.allOf(vectorFuture, bm25Future).join();

        long latency = System.currentTimeMillis() - start;
        log.info("✅ [HYBRID] Tìm kiếm kép hoàn tất trong {} ms", latency);

        return new HybridRetrievalResult(vectorFuture.join(), bm25Future.join());
    }
}
