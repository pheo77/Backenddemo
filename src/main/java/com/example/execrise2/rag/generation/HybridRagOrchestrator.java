package com.example.execrise2.rag.generation;

import com.example.execrise2.ai_orchestrator.adapter.GeminiAdapter;
import com.example.execrise2.ai_orchestrator.adapter.GroqAdapter;
import com.example.execrise2.ai_orchestrator.pool.GroqKeyPool;
import com.example.execrise2.rag.model.*;
import com.example.execrise2.rag.retrieval.ContextCompressor;
import com.example.execrise2.rag.retrieval.HybridRetriever;
import com.example.execrise2.rag.retrieval.RrfFusionService;
import com.example.execrise2.rag.selfrag.SelfRagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * [LAYER 7] HYBRID RAG ORCHESTRATOR — Pipeline chính từ đầu đến cuối.
 *
 * Luồng xử lý đầy đủ:
 * ─────────────────────────────────────────────────────────────────
 * 1. QueryTransformer  → HyDE / Multi-Query (tùy option)
 * 2. HybridRetriever   → Song song: Vector + BM25
 * 3. RrfFusionService  → Trộn kết quả bằng RRF algorithm
 * 4. SelfRagService    → Lọc chunk kém liên quan (Grading)
 * 5. ContextCompressor → Nén context vừa vặn với context window
 * 6. LLM Call          → Gọi AI sinh câu trả lời có trích dẫn
 * 7. SelfRagService    → Kiểm tra hallucination trước khi trả về
 * 8. CitationExtractor → Tạo danh sách nguồn trích dẫn
 * ─────────────────────────────────────────────────────────────────
 *
 * ⚠️  LLM Call hiện tại là MOCK. Thay bằng Spring AI ChatClient khi tích hợp.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HybridRagOrchestrator {

    private final HybridRetriever hybridRetriever;
    private final RrfFusionService rrfFusionService;
    private final ContextCompressor contextCompressor;
    private final QueryTransformer queryTransformer;
    private final CitationExtractor citationExtractor;
    private final SelfRagService selfRagService;
    private final GroqAdapter groqAdapter;
    private final GeminiAdapter geminiAdapter;

    /** Track provider dùng ở cuối (Groq / Gemini / MOCK) cho metadata */
    private volatile String lastUsedProvider = "MOCK";

    /**
     * Xử lý một câu hỏi RAG hoàn chỉnh.
     *
     * @param request Câu hỏi và cấu hình tìm kiếm
     * @return Câu trả lời + nguồn trích dẫn + metadata kỹ thuật
     */
    public RagResponse query(RagRequest request) {
        long pipelineStart = System.currentTimeMillis();
        log.info("═══════════════════════════════════════════════════════");
        log.info("📨 [RAG] Câu hỏi mới: '{}'", request.question());

        // ──────────────────────────────────────────────────────────
        //  BƯỚC 1: Query Transformation
        // ──────────────────────────────────────────────────────────
        String effectiveQuery = request.question();
        if (request.options().useHyDE()) {
            effectiveQuery = queryTransformer.applyHyDE(request.question());
            log.debug("   [HyDE] Query biến đổi thành: '{}'...",
                effectiveQuery.substring(0, Math.min(80, effectiveQuery.length())));
        }

        // ──────────────────────────────────────────────────────────
        //  BƯỚC 2: Tìm kiếm kép Song Song (Vector + BM25)
        // ──────────────────────────────────────────────────────────
        long retrievalStart = System.currentTimeMillis();
        HybridRetrievalResult rawResults = hybridRetriever.retrieve(effectiveQuery, request.options());
        long retrievalLatency = System.currentTimeMillis() - retrievalStart;

        log.info("   [Retrieval] Vector: {} | BM25: {} | Latency: {} ms",
            rawResults.vectorChunks().size(), rawResults.bm25Chunks().size(), retrievalLatency);

        // ──────────────────────────────────────────────────────────
        //  BƯỚC 3: RRF Fusion — Trộn kết quả
        // ──────────────────────────────────────────────────────────
        List<DocumentChunk> fusedChunks = rrfFusionService.fuse(
            rawResults.vectorChunks(),
            rawResults.bm25Chunks(),
            request.options().topK()
        );
        log.info("   [RRF Fusion] {} chunk sau khi trộn", fusedChunks.size());

        // ──────────────────────────────────────────────────────────
        //  BƯỚC 4: Self-RAG Grading — Lọc chunk kém liên quan
        // ──────────────────────────────────────────────────────────
        List<DocumentChunk> gradedChunks = fusedChunks.stream()
            .filter(chunk -> {
                double score = selfRagService.gradeRelevance(request.question(), chunk.getContent());
                boolean pass = score >= 0.4; // Ngưỡng 40%
                if (!pass) log.debug("   [Self-RAG] Loại chunk (score={:.2f}): '{}'...",
                    score, chunk.getContent().substring(0, Math.min(40, chunk.getContent().length())));
                return pass;
            })
            .toList();
        log.info("   [Self-RAG] {} / {} chunk vượt qua grading", gradedChunks.size(), fusedChunks.size());

        // Fallback: nếu tất cả bị loại, dùng lại danh sách fused gốc
        List<DocumentChunk> finalChunks = gradedChunks.isEmpty() ? fusedChunks : gradedChunks;

        // ──────────────────────────────────────────────────────────
        //  BƯỚC 5: Context Compression
        // ──────────────────────────────────────────────────────────
        String compressedContext = contextCompressor.compress(finalChunks, request.question());
        log.debug("   [Compressor] Context: {} ký tự", compressedContext.length());

        // ──────────────────────────────────────────────────────────
        //  BƯỚC 6: LLM Call — Sinh câu trả lời
        // ──────────────────────────────────────────────────────────
        String answer = callLLM(request.question(), compressedContext);
        log.info("   [LLM] Đã sinh câu trả lời ({} ký tự)", answer.length());

        // ──────────────────────────────────────────────────────────
        //  BƯỚC 7: Hallucination Check
        // ──────────────────────────────────────────────────────────
        boolean isGrounded = selfRagService.isGrounded(answer, compressedContext);
        if (!isGrounded) {
            log.warn("   ⚠️  [Self-RAG] Phát hiện khả năng hallucination!");
        }

        // ──────────────────────────────────────────────────────────
        //  BƯỚC 8: Xây dựng Response
        // ──────────────────────────────────────────────────────────
        long totalLatency = System.currentTimeMillis() - pipelineStart;
        List<Citation> citations = citationExtractor.extract(finalChunks);

        RetrievalMeta meta = new RetrievalMeta(
            totalLatency,
            retrievalLatency,
            rawResults.vectorChunks().size(),
            rawResults.bm25Chunks().size(),
            finalChunks.size(),
            lastUsedProvider,
            effectiveQuery,
            isGrounded
        );

        log.info("✅ [RAG] Hoàn tất pipeline trong {} ms | Grounded: {}", totalLatency, isGrounded);
        log.info("═══════════════════════════════════════════════════════");

        return new RagResponse(answer, citations, meta);
    }

    // ──────────────────────────────────────────────────────────────
    //  LLM CALL — Groq (Primary) → Gemini (Fallback)
    // ──────────────────────────────────────────────────────────────

    /**
     * Gọi LLM theo thứ tự: Groq → Fallback Gemini → Error message.
     *
     * Pipeline:
     *   1. Groq Key Pool có key ACTIVE → gọi Groq
     *   2. Groq Pool cạn (PoolExhaustedException) → fallback Gemini
     *   3. Gemini cũng lỗi → trả thông báo thân thiện
     */
    private String callLLM(String question, String context) {
        String systemPrompt = """
                Bạn là Trợ lý AI Đà Nẵng Lắng Nghe — hỗ trợ người dân giải đáp các thắc mắc và thủ tục hành chính.
                Nhiệm vụ: Trả lời câu hỏi của người dân dựa HOÀN TOÀN vào thông tin trong CONTEXT bên dưới.
                
                Quy tắc:
                1. Chỉ dùng thông tin từ CONTEXT, không bịa thêm.
                2. Nếu CONTEXT không đủ thông tin → trả lời: "Tôi chưa có thông tin về vấn đề này. Vui lòng liên hệ UBND Phường hoặc cơ quan chức năng."
                3. Trả lời bằng tiếng Việt, rõ ràng, thân thiện.
                4. Trích dẫn nguồn nếu có: [Nguồn: tên tài liệu]
                5. Tối đa 300 từ.
                """;

        String userMessage = """
                CONTEXT:
                %s
                
                CÂU HỎI: %s
                """.formatted(context, question);

        // ─── THử GROQ TRƯỜC ───────────────────────────────────────
        try {
            String result = groqAdapter.generateResponseAsync(systemPrompt, userMessage).get();
            lastUsedProvider = "GROQ/llama-3.3-70b-versatile";
            return result;

        } catch (GroqKeyPool.PoolExhaustedException poolEx) {
            log.warn("⚠️  [callLLM] Groq Pool cạn → Fallback Gemini | Lý do: {}", poolEx.getMessage());

            // ─── FALLBACK GEMINI ──────────────────────────────────
            try {
                String geminiResult = geminiAdapter.generateResponseAsync(systemPrompt, userMessage).get();
                lastUsedProvider = geminiAdapter.isHealthy() ? "GEMINI/gemini-1.5-flash" : "MOCK_FALLBACK";
                log.info("✅ [callLLM] Gemini fallback thành công.");
                return geminiResult;
            } catch (Exception geminiEx) {
                log.error("❌ [callLLM] Cả Groq lẫn Gemini đều lỗi: {}", geminiEx.getMessage());
                lastUsedProvider = "FAILED";
                return "⏳ Hệ thống AI đang tạm thời quá tải. Vui lòng thử lại sau 1-2 phút.";
            }

        } catch (Exception e) {
            log.error("❌ [callLLM] Lỗi không xác định: {}", e.getMessage());
            lastUsedProvider = "ERROR";
            return "Xin lỗi, hệ thống AI tạm thời gặp sự cố. Vui lòng thử lại sau.";
        }
    }
}
