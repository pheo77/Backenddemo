package com.example.execrise2.rag.generation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * [LAYER 6] QUERY TRANSFORMER — Biến đổi câu hỏi trước khi tìm kiếm.
 *
 * Hai chiến lược chính:
 *
 * 1️⃣  HyDE (Hypothetical Document Embedding):
 *     Thay vì tìm vector của CÂU HỎI (ngắn, ít thông tin),
 *     hãy tạo ra một "câu trả lời giả định" (dài hơn, nhiều ngữ cảnh hơn)
 *     và tìm vector của ĐÁP ÁN GIẢ ĐỊNH đó.
 *     → Kết quả gần với chunk chứa đáp án thật hơn.
 *
 * 2️⃣  Multi-Query:
 *     Viết lại câu hỏi thành 3 cách diễn đạt khác nhau,
 *     tìm kiếm song song tất cả, gộp kết quả → bắt được nhiều chunk hơn.
 *
 * ⚠️  Implementation hiện tại: MOCK (không gọi LLM thật).
 *     → Thay thế bằng Spring AI ChatClient khi có API key:
 *         chatClient.prompt().system(...).user(query).call().content()
 */
@Service
@Slf4j
public class QueryTransformer {

    /**
     * HyDE: Tạo câu trả lời giả định cho câu hỏi.
     * Mục tiêu: Vector của câu trả lời giả định ≈ vector của chunk chứa đáp án thật.
     *
     * @param originalQuery Câu hỏi gốc từ người dùng
     * @return Câu trả lời giả định (chưa kiểm chứng thực tế)
     */
    public String applyHyDE(String originalQuery) {
        log.debug("🧪 [HyDE] Tạo hypothetical answer cho: '{}'", originalQuery);

        // ── TODO: Thay bằng Spring AI call ────────────────────────────
        // return chatClient.prompt()
        //     .system("Viết một đoạn văn ngắn 2-3 câu như thể đang trả lời câu hỏi sau. " +
        //             "Viết theo phong cách sách giáo khoa tiếng Nhật.")
        //     .user(originalQuery)
        //     .call().content();
        // ─────────────────────────────────────────────────────────────

        // MOCK: Thêm prefix "Câu trả lời là:" và mở rộng câu hỏi
        return String.format(
            "Câu trả lời cho câu hỏi '%s' là: Dựa trên ngữ pháp tiếng Nhật, " +
            "khái niệm này liên quan đến cấu trúc câu và cách sử dụng trong ngữ cảnh hàng ngày. " +
            "Người học cần lưu ý các trường hợp ngoại lệ và cách kết hợp với các mẫu câu khác.",
            originalQuery
        );
    }

    /**
     * Multi-Query: Viết lại câu hỏi thành nhiều biến thể để mở rộng tìm kiếm.
     *
     * @param query Câu hỏi gốc
     * @return Danh sách 3 biến thể khác nhau + câu hỏi gốc
     */
    public List<String> expandQuery(String query) {
        log.debug("🔀 [MULTI-QUERY] Mở rộng câu hỏi: '{}'", query);

        // ── TODO: Thay bằng Spring AI call ────────────────────────────
        // String response = chatClient.prompt()
        //     .system("Viết lại câu hỏi sau thành 3 biến thể ngắn gọn. " +
        //             "Mỗi biến thể dùng từ ngữ khác nhau nhưng cùng ý nghĩa. " +
        //             "Trả về JSON array: [\"variant1\", \"variant2\", \"variant3\"]")
        //     .user(query)
        //     .call().content();
        // return parseJsonArray(response);
        // ─────────────────────────────────────────────────────────────

        // MOCK: Thêm tiền tố/hậu tố thủ công
        return List.of(
            query,                                          // Gốc
            "Giải thích " + query,                         // Biến thể 1
            query + " trong tiếng Nhật",                   // Biến thể 2
            "Cách sử dụng " + query + " như thế nào?"      // Biến thể 3
        );
    }

    /**
     * Query Decomposition: Phân rã câu hỏi phức tạp thành các câu hỏi con đơn giản.
     * Dùng cho câu hỏi multi-hop (cần nhiều bước suy luận).
     *
     * @param complexQuery Câu hỏi phức tạp
     * @return Danh sách câu hỏi con
     */
    public List<String> decompose(String complexQuery) {
        log.debug("🔬 [DECOMPOSE] Phân rã câu hỏi: '{}'", complexQuery);

        // ── TODO: Thay bằng Spring AI call ────────────────────────────
        // return chatClient.prompt()
        //     .system("Phân rã câu hỏi phức tạp sau thành tối đa 3 câu hỏi con đơn giản. " +
        //             "Mỗi câu hỏi con phải độc lập và có thể trả lời riêng lẻ. " +
        //             "Trả về JSON array.")
        //     .user(complexQuery)
        //     .call().content();
        // ─────────────────────────────────────────────────────────────

        // MOCK: Chỉ trả về câu hỏi gốc nếu chưa tích hợp LLM
        return List.of(complexQuery);
    }
}
