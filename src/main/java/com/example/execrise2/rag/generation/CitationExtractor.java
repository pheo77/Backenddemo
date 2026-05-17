package com.example.execrise2.rag.generation;

import com.example.execrise2.rag.model.Citation;
import com.example.execrise2.rag.model.DocumentChunk;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.IntStream;

/**
 * [LAYER 7] CITATION EXTRACTOR — Trích xuất nguồn trích dẫn từ chunk đã dùng.
 *
 * Mỗi Citation tương ứng với một [Nguồn: X] trong câu trả lời của AI.
 * Frontend có thể hiển thị danh sách nguồn bên dưới câu trả lời.
 */
@Service
@Slf4j
public class CitationExtractor {

    private static final int SNIPPET_LENGTH = 200; // Ký tự

    /**
     * Tạo danh sách Citation từ các chunk đã được dùng trong context.
     *
     * @param chunks         Danh sách chunk (theo thứ tự xuất hiện trong context)
     * @param rrfScores      Điểm RRF tương ứng (để hiển thị độ liên quan)
     * @return Danh sách Citation sắp xếp theo thứ tự [Nguồn: 1], [Nguồn: 2]...
     */
    public List<Citation> extract(List<DocumentChunk> chunks, List<Double> rrfScores) {
        return IntStream.range(0, chunks.size())
            .mapToObj(i -> buildCitation(chunks.get(i),
                i < rrfScores.size() ? rrfScores.get(i) : 0.0))
            .toList();
    }

    /**
     * Overload đơn giản — không có điểm RRF (dùng 0.0 làm mặc định).
     */
    public List<Citation> extract(List<DocumentChunk> chunks) {
        return chunks.stream()
            .map(chunk -> buildCitation(chunk, 0.0))
            .toList();
    }

    // ──────────────────────────────────────────────────────────────
    //  PRIVATE HELPERS
    // ──────────────────────────────────────────────────────────────

    private Citation buildCitation(DocumentChunk chunk, double score) {
        String snippet = chunk.getContent() != null
            ? chunk.getContent().substring(0, Math.min(SNIPPET_LENGTH, chunk.getContent().length()))
                   .replaceAll("\\s+", " ").trim() + "..."
            : "";

        return new Citation(
            chunk.getId(),
            chunk.getSourceUrl() != null ? chunk.getSourceUrl() : "unknown",
            chunk.getDocType() != null ? chunk.getDocType() : "unknown",
            chunk.getLanguage() != null ? chunk.getLanguage() : "unknown",
            snippet,
            score
        );
    }
}
