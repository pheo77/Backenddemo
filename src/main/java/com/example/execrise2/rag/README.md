# 🤖 Hybrid RAG Module — Spring Boot V1.0

Module tìm kiếm thông minh kép (Vector + BM25) tích hợp vào project Execrise2.

## 📦 Cấu trúc Package

```
com.example.execrise2.rag/
│
├── model/                          # DTOs & Entities
│   ├── DocumentChunk.java          # Entity: Chunk + Embedding + Metadata
│   ├── RagRequest.java             # Input DTO (validated)
│   ├── RagResponse.java            # Output DTO
│   ├── Citation.java               # Nguồn trích dẫn
│   ├── RetrievalMeta.java          # Metadata kỹ thuật
│   ├── RetrievalOptions.java       # Cấu hình tìm kiếm
│   ├── HybridRetrievalResult.java  # Wrapper kết quả kép
│   └── DocumentMetadata.java       # Metadata khi upload
│
├── repository/
│   └── DocumentChunkRepository.java  # JPA + Native PGVector queries
│
├── ingestion/                      # LAYER 1: Nhập liệu
│   ├── DocumentIngestionPipeline.java  # Orchestrator chính
│   ├── SemanticChunker.java            # Cắt văn bản → Chunks
│   ├── EmbeddingClientFacade.java      # Wrapper Embedding API (có Mock)
│   └── DocumentIndexingListener.java   # @Async Event Listener
│
├── retrieval/                      # LAYER 2-5: Tìm kiếm
│   ├── VectorRetriever.java         # Dense Search (PGVector)
│   ├── BM25Retriever.java           # Sparse Search (Keyword)
│   ├── HybridRetriever.java         # Kết hợp song song (Virtual Threads)
│   ├── RrfFusionService.java        # RRF Fusion Algorithm
│   └── ContextCompressor.java       # Nén context trước khi vào Prompt
│
├── generation/                     # LAYER 6-7: Sinh câu trả lời
│   ├── QueryTransformer.java        # HyDE, Multi-Query
│   ├── HybridRagOrchestrator.java   # Pipeline chính (8 bước)
│   └── CitationExtractor.java       # Trích xuất nguồn trích dẫn
│
├── selfrag/
│   └── SelfRagService.java          # Relevance grading + Hallucination check
│
├── exception/
│   ├── NoContextFoundException.java
│   ├── EmbeddingException.java
│   └── RagExceptionHandler.java     # @RestControllerAdvice (RFC 7807)
│
└── controller/
    └── RagController.java           # REST endpoints
```

## 🚀 REST Endpoints

| Method | URL | Mô tả |
|--------|-----|-------|
| `POST` | `/api/rag/query` | Gửi câu hỏi → nhận câu trả lời |
| `GET`  | `/api/rag/query-simple?q=...` | Test nhanh trên browser |
| `POST` | `/api/rag/ingest` | Upload file → index ngầm |
| `GET`  | `/api/rag/stats` | Số chunk trong DB |

## 🧪 Test ngay (không cần PostgreSQL)

Với cấu hình SQL Server hiện tại, hệ thống sẽ báo lỗi kết nối DB vì entity `DocumentChunk`
cần PostgreSQL/PGVector. Để test pipeline logic:

```bash
# Test đơn giản với GET
curl "http://localhost:8080/api/rag/query-simple?q=て形とは何ですか&docType=grammar&lang=ja"

# Test full với POST
curl -X POST http://localhost:8080/api/rag/query \
  -H "Content-Type: application/json" \
  -d '{
    "question": "て形 dùng khi nào?",
    "options": {
      "docType": "grammar",
      "language": "ja",
      "topK": 5,
      "allowedPermissions": ["PUBLIC"],
      "useHyDE": false,
      "useMultiQuery": false
    }
  }'
```

## 🔌 Chuyển sang PostgreSQL (Production)

1. Cài PostgreSQL + pgvector: `CREATE EXTENSION vector;`
2. Bỏ comment Flyway + PostgreSQL trong `pom.xml`
3. Chạy với profile RAG: `--spring.profiles.active=rag`
4. Flyway tự động chạy `V2__init_rag.sql` lúc startup

## 🔌 Tích hợp Spring AI (Real LLM)

Tìm các comment `// TODO: Thay bằng Spring AI call` trong:
- `EmbeddingClientFacade.java` — Embedding thật
- `QueryTransformer.java` — HyDE/Multi-Query thật
- `SelfRagService.java` — Grading thật
- `HybridRagOrchestrator.java#callLLM()` — LLM thật

## 📊 Luồng dữ liệu (8 bước)

```
POST /api/rag/query
    ↓ [1] QueryTransformer (HyDE / Multi-Query)
    ↓ [2-3] HybridRetriever — Song song:
    │         ├── VectorRetriever (PGVector, Cosine)
    │         └── BM25Retriever  (Keyword/Lucene)
    ↓ [4] RrfFusionService (RRF Algorithm → Top-10)
    ↓ [5] SelfRagService.gradeRelevance() (Lọc chunk kém)
    ↓ [6] ContextCompressor (Nén context < 6000 chars)
    ↓ [7] LLM Call → callLLM() (Mock → Spring AI ChatClient)
    ↓ [8] SelfRagService.isGrounded() (Hallucination check)
    ↓ CitationExtractor → RagResponse
    ↓ HTTP 200 OK
```
