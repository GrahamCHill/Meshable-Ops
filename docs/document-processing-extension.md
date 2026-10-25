# Document folders, content indexing, and OCR

Original files remain in object storage. PostgreSQL owns document identities, visibility, processing jobs, extracted pages/chunks, and vectors. Events and audit history also live in PostgreSQL and remain excluded from document search.

Browsing, uploads, and reindexing synchronize metadata and enqueue content work when **Index document content** is enabled. A separate scheduler processes one document at a time. PostgreSQL claims use a token and a 30-minute lease; interrupted jobs resume after lease expiry. Three failed attempts use increasing delays before `CONTENT_FAILED`. Reindexing explicitly queues another attempt. Source/model changes invalidate older claim tokens. Removing a document cascades to its jobs, pages, and chunks. Provider calls run outside database transactions; publication of pages, chunks, and completion is atomic and rechecks live visibility and content settings.

## Text extraction and external OCR

The built-in Python helper reads existing PDF text layers, DOCX paragraphs, and supported UTF-8 formats. It reports sparse image pages as requiring OCR. It does not bundle an OCR engine.

Set these values on the backend environment:

| Variable | Default | Meaning |
| --- | --- | --- |
| `DOCUMENT_MAX_BYTES` | `52428800` | Maximum input size (50 MiB; configurable up to 100 MiB). Set consistently on the backend and ML helper. |
| `DOCUMENT_OCR_URL` | empty | Full HTTP endpoint for an OCR adapter. With no endpoint, scans receive `OCR_REQUIRED` and retain metadata search. |
| `DOCUMENT_OCR_TOKEN` | empty | Optional bearer token sent only to the OCR adapter. Keep it in the private deployment environment. |

The adapter may call a cloud document intelligence API, Docling, Paddle, or another service. Provider-specific authentication, asynchronous cloud polling, model selection, and response translation belong in that adapter. A raw vendor API URL is not automatically compatible with this contract. No cloud subscription, engine download, or provider is selected by the application.

The backend sends a JSON POST with `filename` and base64-encoded `content`. The configured adapter receives the original bytes only when OCR is needed (or native extraction is unavailable). Configure only a trusted endpoint. Authentication tokens and document contents are never copied into processing-job errors.

The adapter returns a complete document, including existing text pages in mixed PDFs:

```json
{
  "engine": "my-ocr-adapter-v1",
  "languages": "eng",
  "truncated": false,
  "pages": [
    { "pageNumber": 1, "text": "Recognized page text", "method": "OCR", "confidence": 96.2 }
  ]
}
```

Page numbers are positive, unique, and refer to original pages. Confidence is optional and ranges from 0 to 100. Empty page text is valid; an empty page list is a provider failure. Set `truncated: true` whenever the service omits content. The service has a 910-second HTTP timeout; adapters must complete or return an error within it. Long-running vendor jobs can be polled inside the adapter. Failed HTTP/invalid responses use the durable retry policy.

## Stored content and limits

`document_pages` stores page text and JSONB provenance (method, confidence, engine, languages). `document_chunks.page_number` retains the originating page through vector/keyword search and AI source responses. AI sources display page numbers. Documents without stable pagination, such as DOCX, use null page numbers.

The helper limits native extraction to 65 seconds, 2 million characters, and 500 PDF pages, with process memory/CPU limits on Linux. External results are bounded to 1,000 pages and 2 million characters. Indexing supports up to 4,096 overlapping 1,600-character chunks, processed in batches of 32 for embeddings. Chunks do not cross page boundaries. Limits produce `CONTENT_TRUNCATED` instead of silently claiming complete indexing.

States include `METADATA_ONLY`, `CONTENT_QUEUED`, `CONTENT_RETRY`, `CONTENT_FAILED`, `OCR_REQUIRED`, `CONTENT_INDEXED`, `CONTENT_TRUNCATED`, `UNSUPPORTED_FORMAT`, `CONTENT_TOO_LARGE`, and `NO_EXTRACTABLE_TEXT`. Metadata embedding failures remain `INDEX_ERROR`. Missing extraction/OCR leaves storage browsing and metadata search available. After configuring or changing the OCR adapter, run **Reindex now** to queue existing scans.

Reindexing still synchronizes folder metadata before responding; its counts describe discovery and the current index, not completion of queued content jobs. The content worker uses a separate scheduler so OCR does not block other scheduled tasks. Other existing application schedulers still require one backend instance.

Large optional artifacts (raw provider layout responses, searchable PDF derivatives, and page images) should remain in object storage when an adapter produces them. They are not generated or retained automatically. Generated PDFs from the Documents module continue to use their existing PostgreSQL storage.

See [search settings and schedules](search-and-indexing.md), [database layout](database-layout.md), and [legacy history migration](history-migration.md).
