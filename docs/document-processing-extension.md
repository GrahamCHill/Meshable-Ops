# Document folders, content indexing, and processing

Original files remain in S3-compatible storage or Azure Blob. Application-managed folders use the configured prefix plus the folder UUID; external shared folders reference an administrator-selected connection and prefix. PostgreSQL owns identities, nicknames, visibility, processing state, metadata vectors, and extracted chunks. Hidden folders and disabled connections are excluded from search and AI retrieval.

Each discovered object has a `storage_documents` row and a `document_vectors` metadata embedding. When content indexing is enabled, the bounded extraction service produces text for supported UTF-8 formats, PDF text, and DOCX paragraphs. `DocumentIndexService` chunks the text and stores `document_chunks` with source checksum and embedding-model identity. Upload, browsing, and explicit or scheduled reindexing synchronize the index. Full reindexing removes metadata for deleted objects.

The responsibility boundaries are:

- `FolderStorageService`: selected-prefix validation, bounded object reads, browsing, uploads, downloads, and ZIP archives.
- `DocumentTextService` plus `ml-backend/app/document_text.py`: isolated extraction with size, time, and Linux resource limits.
- `DocumentIndexService`: discovery/checksums, processing status, changed document/chunk indexing, and missing-document cleanup.
- `EmbeddingService`: internal text matching or external embedding providers, fixed dimension validation, normalization, and model identity.
- `VectorService`: PostgreSQL vector persistence and queries with live visibility joins.
- `AiAnswerService`: relevant accessible excerpts, source attribution, provider generation, and access rechecks before returning answers.

Processing states distinguish content indexed, partially indexed, metadata only, unsupported format, over-limit files, no extractable text, unavailable extraction, and indexing errors. Missing extraction retains metadata search; default feature hashing runs within the backend without AI or the ML container. Scanned PDFs need OCR, which is not implemented. Image classification and an asynchronous indexing queue also remain future extensions. Current indexing runs synchronously, and reindex results report content counts and failures. [Calendar and change-triggered jobs](search-and-indexing.md) control scheduled reindexing; MongoDB integration remains deferred.

See [AI setup, limits, provider configuration, and credential protection](ai-search-and-secrets.md) for the complete operational guide.
