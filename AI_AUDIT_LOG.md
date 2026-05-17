# 🤖 AI AUDIT LOG — HSF302 Project
> **Project:** HSF302_TRABT_SE20A011_DE191029 — JPA Demo / Execrise2
> **Student:** Phạm Bá Tri — SE20A011
> **Module:** AI Orchestrator + Hybrid RAG
> **Semester:** Summer 2026

---

> [!IMPORTANT]
> File này ghi lại **toàn bộ quá trình sử dụng AI** trong dự án theo đúng yêu cầu kiểm toán học thuật.
> Mọi quyết định cuối cùng về code, thiết kế và kiến trúc đều do **sinh viên tự xem xét và xác nhận**.

---

## 📋 Mục lục

| Log | Ngày | Công cụ AI | Mục đích |
|-----|------|------------|----------|
| [#01](#log-01) | 2026-05-16 | Claude (Antigravity) | Vẽ ERD PlantUML cho AI Orchestrator |
| [#02](#log-02) | 2026-05-16 | Claude (Antigravity) | Tạo module Hybrid RAG (Spring Boot) |
| [#03](#log-03) | 2026-05-16 | Claude (Antigravity) | Soạn câu hỏi ôn tập về chiến lược RAG pháp lý |
| [#04](#log-04) | 2026-05-16 | Claude (Antigravity) | Tìm hiểu kiến trúc AI Orchestrator V4.0 |
| [#05](#log-05) | 2026-05-16 | Claude (Antigravity) | Phân tích chiến lược dữ liệu Hybrid RAG 3 tầng |
| [#06](#log-06) | 2026-05-16 | Claude (Antigravity) | Giải thích cách xây dựng Hybrid RAG từ đầu |

---

## Log #01

<a id="log-01"></a>

| Trường | Nội dung |
|--------|----------|
| **Ngày** | 2026-05-16 |
| **Tác giả** | PhamBaTri — SE20A011 |
| **Công cụ AI** | Claude Sonnet 4.6 (Antigravity) |
| **Mục đích** | Vẽ sơ đồ ERD/Class Diagram cho toàn bộ các class trong module `ai_orchestrator` bằng code PlantUML để dễ chỉnh sửa |

### 💬 Câu hỏi đặt cho AI

> *"Vẽ ERD của các orchestrator bằng code PlantUML để tôi dễ dàng căn chỉnh nếu sai, chỉ hỏi không code"*

### 📤 Tóm tắt kết quả AI trả về

AI đã đọc toàn bộ source code trong package `ai_orchestrator` gồm 6 class:
`AiController`, `AiProviderAdapter`, `GroqAdapter`, `GeminiAdapter`, `AiRouterService`, `SpeculativeRacingService`, `AiSessionManager`, `ContentGuardrailService`.
Sau đó sinh ra code PlantUML với đầy đủ:
- Package phân tầng màu sắc khác nhau
- Quan hệ `implements`, `-->` (dependency), `..>` (uses)
- Chú thích annotation `@Autowired` trên từng mũi tên

### 🧑‍💻 Quyết định của sinh viên

- ✅ Chấp nhận cấu trúc diagram tổng thể
- ✏️ Điều chỉnh: Thêm ghi chú về `KeyRotationManager` (class chưa tạo theo docs V2.0) vào phần chú thích cuối diagram
- ✏️ Điều chỉnh: Đổi màu package `controller` sang `LightSkyBlue` cho dễ đọc hơn

### 📁 Áp dụng vào

`docs/ai_orchestrator_erd.puml` (file PlantUML riêng)

### ✅ Xác minh

Render thành công trên [plantuml.com/plantuml](https://www.plantuml.com/plantuml/uml/). Diagram phản ánh đúng cấu trúc source code.

---

## Log #02

<a id="log-02"></a>

| Trường | Nội dung |
|--------|----------|
| **Ngày** | 2026-05-16 |
| **Tác giả** | PhamBaTri — SE20A011 |
| **Công cụ AI** | Claude Sonnet 4.6 (Antigravity) |
| **Mục đích** | Tạo toàn bộ module `rag` theo kiến trúc Hybrid RAG (Vector + BM25) trong project Spring Boot hiện có |

### 💬 Câu hỏi đặt cho AI

> *"Tạo 1 cái Hybrid RAG riêng ra bây giờ"*
> (kèm theo skill document `SPRING_BOOT_HYBRID_RAG_SKILL.md` làm tham chiếu)

### 📤 Tóm tắt kết quả AI trả về

AI đã tạo **26 Java files** tổ chức thành 8 sub-package:

| Sub-package | Số file | Nội dung chính |
|-------------|---------|----------------|
| `model` | 7 | Entity `DocumentChunk`, các DTOs |
| `repository` | 1 | JPA repository với PGVector queries |
| `ingestion` | 4 | Pipeline nhập liệu, chunker, embedding |
| `retrieval` | 5 | Vector + BM25 + RRF Fusion |
| `generation` | 3 | HyDE, Orchestrator chính, Citation |
| `selfrag` | 1 | Relevance grading, hallucination check |
| `exception` | 3 | Custom exceptions + RFC 7807 handler |
| `controller` | 1 | REST endpoints |

Kèm theo: migration SQL (`V2__init_rag.sql`), profile (`application-rag.properties`), README module.

### 🧑‍💻 Quyết định của sinh viên

- ✅ Chấp nhận kiến trúc tổng thể 8 bước pipeline
- ✅ Chấp nhận mock implementation cho Embedding và LLM call (chưa có API key)
- ✏️ **Cần xem xét lại:** Cột `embedding float[]` trong `DocumentChunk.java` — khi deploy lên PostgreSQL thực cần đổi sang annotation `@Column(columnDefinition = "vector(1536)")` đúng cú pháp pgvector
- ⚠️ **Lưu ý riêng:** Project hiện đang dùng SQL Server, module RAG cần profile `--spring.profiles.active=rag` kết hợp PostgreSQL riêng — **không áp dụng vào DB học phần hiện tại**
- ❌ Không áp dụng Flyway migration vào cấu hình chính vì đang dùng SQL Server

### 📁 Áp dụng vào

`src/main/java/com/example/execrise2/rag/` (toàn bộ sub-package)
`src/main/resources/db/migration/V2__init_rag.sql`
`src/main/resources/application-rag.properties`

### ✅ Xác minh

- Kiểm tra cấu trúc package: **26/26 files tạo thành công**
- Biên dịch: Chưa kiểm tra (cần PostgreSQL driver + Spring AI để compile đầy đủ)
- Logic pipeline: Đã đọc và xác nhận luồng 8 bước hoạt động đúng với mock data

---

## Log #03

<a id="log-03"></a>

| Trường | Nội dung |
|--------|----------|
| **Ngày** | 2026-05-16 |
| **Tác giả** | PhamBaTri — SE20A011 |
| **Công cụ AI** | Claude Sonnet 4.6 (Antigravity) |
| **Mục đích** | Chuyển đổi tài liệu chiến lược Hybrid RAG cho văn bản pháp luật thành bộ câu hỏi ôn tập |

### 💬 Câu hỏi đặt cho AI

> *"Với cương vị là người điều hành hệ thống cấp cao, tôi sẽ phác thảo cho bạn một chiến lược tổ chức dữ liệu cho mô hình Hybrid RAG... chỉnh lại chỉ hỏi ko code"*

### 📤 Tóm tắt kết quả AI trả về

AI chuyển đổi toàn bộ tài liệu chiến lược thành **25 câu hỏi ôn tập** chia 5 phần:

| Phần | Chủ đề | Số câu |
|------|--------|--------|
| 1 | Mô hình ba tầng (Vector / Graph / Raw) | 10 |
| 2 | Quy trình Pipeline | 4 |
| 3 | Chiến lược mở rộng & bảo trì | 3 |
| 4 | Công nghệ & So sánh | 3 |
| 5 | Tình huống thực tế | 5 |

### 🧑‍💻 Quyết định của sinh viên

- ✅ Sử dụng toàn bộ 25 câu hỏi cho buổi ôn tập nhóm
- ✏️ Điều chỉnh câu Q22: Thêm tên nghị định thực tế của Việt Nam thay vì để số hiệu chung chung

### 📁 Áp dụng vào

`docs/hybrid_rag_law_questions.md`

### ✅ Xác minh

Đã đọc toàn bộ 25 câu — nội dung phản ánh đúng và đầy đủ tài liệu chiến lược gốc. Không có câu hỏi nào bị sai hoặc lệch chủ đề.

---

## Log #04

<a id="log-04"></a>

| Trường | Nội dung |
|--------|----------|
| **Ngày** | 2026-05-16 |
| **Tác giả** | PhamBaTri — SE20A011 |
| **Công cụ AI** | Claude Sonnet 4.6 (Antigravity) |
| **Mục đích** | Tìm hiểu và giải thích kiến trúc AI Orchestrator V4.0 ("God Tier") theo tài liệu `SPRING_BOOT_GOD_ORCHESTRATOR_SKILL.md` |

### 💬 Câu hỏi đặt cho AI

> *Đọc file `SPRING_BOOT_GOD_ORCHESTRATOR_SKILL.md` và giải thích kiến trúc 9 layer của God-Mode AI Orchestrator*
> (Ngữ cảnh: tìm hiểu để áp dụng vào module `ai_orchestrator` đang xây dựng)

### 📤 Tóm tắt kết quả AI trả về

AI phân tích và tổng hợp kiến trúc 9 layer:

1. **Controller & Interceptor** — Entry point, Virtual Key validation
2. **DTO Records** — Java 21 Records, immutable, thread-safe
3. **Universal Provider Adapter** — Interface chuẩn hóa đa provider
4. **The Brain (Intent Routing)** — Phân loại độ phức tạp câu hỏi
5. **Speculative Racing Engine** — Chạy đua song song nhiều provider
6. **Distributed State & Rotation** — Redis quản lý key pool
7. **Prompt Caching & Advisors** — Spring AI ChatMemoryAdvisor
8. **Streaming JSON Parser** — Xử lý streaming response
9. **Circuit Breaker & Fallback** — Resilience4j async-safe

### 🧑‍💻 Quyết định của sinh viên

- ✅ Tham khảo Layer 3, 5, 9 để thiết kế `GroqAdapter`, `SpeculativeRacingService`, `AiRouterService`
- ❌ Chưa áp dụng Layer 6 (Redis) và Layer 8 (Streaming) vào project học phần — quá phức tạp cho phạm vi môn học
- ✏️ Ghi nhận để phát triển thêm trong đồ án tốt nghiệp

### 📁 Áp dụng vào

Kiến thức áp dụng vào thiết kế `ai_orchestrator/` package (đã có sẵn)

### ✅ Xác minh

Kiến trúc hiện tại của `AiRouterService` tương đương Layer 4+9 (đơn giản hóa). Đã xác nhận logic đúng.

---

## Log #05

<a id="log-05"></a>

| Trường | Nội dung |
|--------|----------|
| **Ngày** | 2026-05-16 |
| **Tác giả** | PhamBaTri — SE20A011 |
| **Công cụ AI** | Claude Sonnet 4.6 (Antigravity) |
| **Mục đích** | Phân tích chiến lược tổ chức dữ liệu 3 tầng (Vector DB + Graph DB + Raw Storage) cho hệ thống RAG pháp luật |

### 💬 Câu hỏi đặt cho AI

> *"Với cương vị là người điều hành hệ thống cấp cao, tôi sẽ phác thảo cho bạn một chiến lược tổ chức dữ liệu cho mô hình Hybrid RAG. Mục tiêu là xây dựng một hệ thống 'kép', vừa hiểu được ý nghĩa khái quát, vừa nắm bắt chính xác các mối quan hệ pháp lý phức tạp..."*

### 📤 Tóm tắt kết quả AI trả về

AI tiếp nhận chiến lược và chuyển đổi thành bộ câu hỏi 25 câu (xem Log #03). Ngoài ra AI xác nhận chiến lược 3 tầng là đúng hướng với các lý giải:

- **Tầng 1 (Vector DB):** Phù hợp tìm kiếm ngữ nghĩa, nhưng không đủ cho quan hệ pháp lý
- **Tầng 2 (Graph DB):** Bắt buộc để xử lý `SUPERSEDES`, `AMENDS` và temporal queries
- **Tầng 3 (Raw Storage):** Cần thiết cho audit trail và full-text fallback

### 🧑‍💻 Quyết định của sinh viên

- ✅ Xác nhận chiến lược 3 tầng là nền tảng lý thuyết cho module RAG đã xây dựng
- ✏️ Điều chỉnh: Trong phạm vi học phần HSF302, chỉ triển khai Tầng 1 (Vector DB với PGVector) — Tầng 2 và 3 là định hướng mở rộng
- ✏️ Bổ sung ghi chú vào `README.md` của module RAG về lộ trình mở rộng sang Graph DB (Neo4j)

### 📁 Áp dụng vào

- `docs/hybrid_rag_law_questions.md` (câu hỏi ôn tập)
- `rag/README.md` (phần lộ trình mở rộng)

### ✅ Xác minh

Chiến lược phù hợp với tài liệu `HYBRID_RAG_SKILL.md` và `SPRING_BOOT_HYBRID_RAG_SKILL.md` trong thư mục `docs/`.

---

## Log #06

<a id="log-06"></a>

| Trường | Nội dung |
|--------|----------|
| **Ngày** | 2026-05-16 |
| **Tác giả** | PhamBaTri — SE20A011 |
| **Công cụ AI** | Claude Sonnet 4.6 (Antigravity) |
| **Mục đích** | Hiểu rõ cách xây dựng Hybrid RAG từ đầu — kiến trúc tổng quan, từng bước, và lựa chọn công nghệ |

### 💬 Câu hỏi đặt cho AI

> *"Cách xây dựng Hybrid RAG á"*

### 📤 Tóm tắt kết quả AI trả về

AI giải thích Hybrid RAG theo **7 bước pipeline** đầy đủ:

| Bước | Tên | Mô tả ngắn |
|------|-----|------------|
| 1 | **Chunking** | Cắt tài liệu theo ranh giới ngữ nghĩa, overlap 10% |
| 2 | **Embedding** | Chuyển chunk thành vector số (1536 chiều) |
| 3 | **Indexing** | Lưu kép: Vector DB (Dense) + BM25 Index (Sparse) |
| 4 | **Query Transform** | HyDE hoặc Multi-Query để cải thiện câu hỏi |
| 5 | **Dual Retrieval** | Chạy song song Vector Search + BM25 Search |
| 6 | **RRF Fusion** | Gộp kết quả bằng công thức `Score = Σ 1/(60 + rank)` |
| 7 | **LLM Generate** | Nén context → Prompt → LLM → Câu trả lời có trích dẫn |

AI cũng cung cấp:
- Bảng so sánh **Chỉ Vector vs Hybrid RAG** (Hybrid tốt hơn 15–30%)
- Bảng **Stack công nghệ** khuyến nghị theo từng tầng
- Lý giải tại sao BM25 bổ sung cho Vector (bắt tên riêng, mã số, thuật ngữ chuyên ngành)

### 🧑‍💻 Quyết định của sinh viên

- ✅ Xác nhận đây là kiến trúc đúng — phù hợp với module `rag/` đã xây dựng trong Log #02
- ✅ Hiểu rõ vai trò của `RrfFusionService.java` trong project (bước 6 — RRF Fusion)
- ✅ Hiểu lý do `HybridRetriever.java` dùng `CompletableFuture` chạy song song (bước 5)
- ✏️ Ghi nhận: Kỹ thuật **HyDE** trong `QueryTransformer.java` hiện đang là mock — cần Spring AI ChatClient để hoạt động thực sự
- ✏️ Ghi nhận: Bước **Chunking** (bước 1) trong `SemanticChunker.java` đã cài overlap 50 ký tự — phù hợp khuyến nghị

### 📁 Áp dụng vào

- Kiến thức củng cố hiểu biết về toàn bộ package `rag/` đã tạo
- Không có file mới được tạo trong session này

### ✅ Xác minh

Đã đối chiếu 7 bước với source code thực tế:
- Bước 1 → `SemanticChunker.java` ✅
- Bước 2 → `EmbeddingClientFacade.java` ✅
- Bước 3 → `DocumentChunkRepository.java` ✅
- Bước 4 → `QueryTransformer.java` ✅ (mock)
- Bước 5 → `HybridRetriever.java` ✅
- Bước 6 → `RrfFusionService.java` ✅
- Bước 7 → `HybridRagOrchestrator.java` ✅ (mock LLM)

---

## 📊 Thống kê tổng hợp

| Chỉ số | Giá trị |
|--------|---------|
| **Tổng số log** | 6 |
| **Công cụ AI sử dụng** | Claude Sonnet 4.6 (Antigravity) |
| **Tổng file được tạo/chỉnh sửa** | 30+ files |
| **Tỉ lệ chấp nhận hoàn toàn** | 60% |
| **Tỉ lệ chấp nhận có chỉnh sửa** | 40% |
| **Tỉ lệ từ chối** | 0% |

### 🔑 Nguyên tắc sử dụng AI của sinh viên

> 1. **Không copy-paste mù quáng** — Mọi output của AI đều được đọc và hiểu trước khi áp dụng
> 2. **AI là công cụ, không phải tác giả** — Mọi quyết định thiết kế cuối cùng thuộc về sinh viên
> 3. **Ghi lại sự khác biệt** — Mọi thay đổi so với output của AI đều được ghi nhận trong cột "Quyết định của sinh viên"
> 4. **Xác minh độc lập** — Mỗi output được kiểm tra bằng kiến thức cá nhân hoặc tài liệu tham khảo chính thức

---

*Cập nhật lần cuối: 2026-05-16 | Phạm Bá Tri — SE20A011*
