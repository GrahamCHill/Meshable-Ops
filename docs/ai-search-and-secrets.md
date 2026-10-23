# AI search and credential security

## Start with search only

Search and its bottom-right launcher default to enabled. AI answers and content indexing default to disabled. Leave embeddings on **Internal text matcher** to search metadata with no AI credentials, model download, or ML container. Add/share storage folders and reindex them; first-volume PostgreSQL initialization supplies sample folder metadata, not original document content.

1. For local Compose, copy `.env.example` to the untracked `.env`. For production, use `.env.production.example` and the [deployment guide](deployment.md).
2. Configure a persistent wrapping key before saving credentials or running the MinIO/Azurite development profiles. Generate it once with `openssl rand -base64 32`, set `APP_SECRETS_ENCRYPTION_KEY`, and keep a separate secure copy. Development storage seeds also save keys and need this value even when AI is off.
3. Rebuild/start the stack, configure accessible folders, and run **Reindex now** under **Administration > Storage connections > Search index**.
4. Open the bottom-right **Search & AI** button. **Administration > AI & search** independently controls search, launcher visibility, AI answers, and content indexing.

On Windows PowerShell without OpenSSL, generate a new wrapping-key value with:

```powershell
$wrappingBytes = New-Object byte[] 32
$wrappingGenerator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$wrappingGenerator.GetBytes($wrappingBytes)
[Convert]::ToBase64String($wrappingBytes)
$wrappingGenerator.Dispose()
```

Generate a new key only for a new deployment or deliberate rotation. An existing encrypted database needs its existing matching key. Environment-only AI keys are not persisted into PostgreSQL; a deployment without any stored credentials can use local search without a wrapping key.

## Add optional AI answers

1. Open **Administration > AI & search** and choose **Server environment** or **Admin settings**. The environment view is read-only; select Admin settings to edit a separate saved configuration. Switching sources does not copy credentials or borrow the other source's keys.
2. Configure chat and embeddings separately. A chat provider can use local text matching or a different external embedding provider for retrieval. Anthropic/Groq chat does not require using the same service for embeddings.
3. Enable **Index document content** and **Enable AI answers**. Save and test the saved chat/embedding configuration; these checks send fixed test text, not documents.
4. Reindex after enabling content indexing or changing the embedding provider/model. **Ask AI** retrieves relevant accessible excerpts and returns numbered source links into Storage.

No AI configuration or an unavailable provider leaves ordinary search available. Chat failures return a non-grounded guidance response; embedding failures use access-checked keyword matches for ordinary search. Disabling search explicitly rejects search and chat requests. See [feature controls and indexing jobs](search-and-indexing.md).

API calls always go through the backend. Browser API keys are not persisted in local/session storage, included in settings responses, or prefilled after saving. The forms show only configured/not-configured state. A blank key preserves a saved key for the same provider and endpoint; changing either requires a new key or an explicitly keyless provider. Use **Clear saved key** to remove a saved AI key, or **Server identity** to clear saved storage credentials.

Credential forms also avoid the shared frontend mutation cache, including request bodies attached to network errors. Scheduled indexing starts only after credential migration and startup initialization finish. Folder sharing and access revocation do not depend on the embedding provider being available.

## Providers

| Selection | Base URL | Notes |
| --- | --- | --- |
| OpenAI-compatible | `https://api.openai.com/v1` or another compatible API prefix | Chat Completions and Embeddings request formats; model names are configurable. |
| Groq preset | `https://api.groq.com/openai/v1` | Uses the OpenAI-compatible chat adapter. Configure embeddings separately. |
| Ollama | `http://ollama:11434/v1` or a reachable Ollama host | Local servers can operate without a key. In Docker, `localhost` refers to the backend container, not the host. For host installations use a reachable host address such as `host.docker.internal`. |
| Azure OpenAI | `https://RESOURCE.openai.azure.com/openai/v1` | Uses the v1 API and `api-key` authentication. Enter the Azure deployment name as the model. |
| Anthropic | `https://api.anthropic.com/v1` | Uses Messages, `x-api-key`, and the pinned `2023-06-01` version. Configure a different embedding provider or the internal matcher. |
| Internal text matcher | No external endpoint | Feature hashing inside the backend, requiring no ML service, model download, or API key. It is not a semantic language-model embedding. |

The current PostgreSQL schema uses `vector(384)`. External embedding models must return 384 finite, nonzero values. Enable the dimensions parameter for models that support it. For Ollama, select a model with native 384-dimensional embeddings or one that supports requesting 384 dimensions. Incompatible responses fail explicitly; vectors are never silently truncated or replaced by random vectors.

Provider URLs reject credentials, query parameters, and fragments. Keys require HTTPS except for loopback test servers. Provider redirects are not followed. Raw provider errors, which may contain prompts or credentials, are not returned or logged by the AI adapter. Connection tests use harmless fixed text, not storage documents.

Official API references: [OpenAI chat](https://developers.openai.com/api/reference/resources/chat/subresources/completions/methods/create), [OpenAI embeddings](https://developers.openai.com/api/reference/python/resources/embeddings/methods/create), [Groq compatibility](https://console.groq.com/docs/openai), [Ollama compatibility](https://docs.ollama.com/api/openai-compatibility), [Azure v1](https://learn.microsoft.com/en-us/rest/api/microsoft-foundry/azureopenai/chat), [Anthropic Messages](https://platform.claude.com/docs/en/api/messages/create).

## Environment configuration

```dotenv
APP_SEARCH_ENABLED=true
APP_SEARCH_LAUNCHER_ENABLED=true
AI_ENABLED=true
AI_INDEX_CONTENT=true
AI_CHAT_PROVIDER=OPENAI_COMPATIBLE
AI_CHAT_BASE_URL=https://api.groq.com/openai/v1
AI_CHAT_MODEL=your-chat-model
AI_CHAT_API_KEY=your-provider-key

# Or use OPENAI_COMPATIBLE, OLLAMA, or AZURE_OPENAI for semantic embeddings.
AI_EMBEDDING_PROVIDER=LOCAL
AI_EMBEDDING_BASE_URL=
AI_EMBEDDING_MODEL=local-feature-hash-v2
AI_EMBEDDING_API_KEY=
AI_EMBEDDING_SEND_DIMENSIONS=true
```

Environment keys remain in the deployment environment. They are not copied into the database by AI settings. Admin-entered keys use the encrypted fields described below. Selecting the environment source in Admin makes the environment configuration active; saving an admin configuration selects that separate configuration.

## Indexing and access

Original files stay in the selected S3 bucket or Azure Blob container. PostgreSQL stores their folder/document identity, processing status, metadata vectors, and extracted text chunks. Upload, discovery, or explicit/scheduled reindexing updates the index. A full reindex also removes documents no longer present in storage.

The internal extraction service supports UTF-8 text/Markdown/CSV/JSON/XML/HTML/log/YAML/TSV/RST, PDF text, and DOCX paragraphs. It does not perform OCR or execute scripts, macros, external links, or document instructions. Scanned PDFs without extractable text and unsupported formats retain metadata search. Content reads are bounded to 5 MiB; extraction is limited to 20 seconds, 120,000 characters, and 200 PDF pages. Linux extraction workers have an additional memory/CPU limit. Up to 32 overlapping 1,600-character chunks are indexed per document. Long documents are marked partially indexed; over-limit files keep metadata only.

Changes are detected using object path, name, content type, size, and modification time. Content chunks additionally store a SHA-256 hash of the downloaded bytes. If a provider changes bytes without changing that metadata, run a full reindex. The embedding provider, endpoint, model, dimension settings, and local algorithm version form an index identity. Queries only use matching vectors, including during provider/model changes. Reindex after changing embeddings or enabling content indexing. Disabling content indexing also excludes existing chunks from search immediately.

Search SQL joins live folder visibility and connection state for folder vectors, document vectors, and content chunks. AI answers only use relevant content chunks, never filename metadata as document evidence. Access is rechecked before calling the model and again before returning the answer. Revoking a folder or connection during generation suppresses the entire answer. Source links resolve the folder through the regular authenticated storage APIs, which enforce the same boundaries.

Extraction/read failures mark a document `CONTENT_UNAVAILABLE` while metadata vectors can still be indexed. Vector/configuration failures mark it `INDEX_ERROR`. Both leave browsing available and are reported as incomplete indexing. Content can be retried by a later browse or reindex. Very large collections currently index synchronously; an asynchronous worker and OCR remain future extensions. MongoDB events/audit logs are excluded, and MongoDB document-search integration is deferred.

## Stored credentials and passwords

API keys and both storage credential fields use AES-256-GCM at rest with a fresh 96-bit nonce and authentication tag per write. The wrapping key is a base64-encoded 32-byte server secret, separate from PostgreSQL and JWT signing. JPA converters encrypt writes and decrypt server-side reads. Stored ciphertext has a version and key identifier; malformed or unauthentic ciphertext fails closed. Ordinary reads do not accept plaintext; only the startup migration handles legacy plaintext rows.

For rotation, configure a new `APP_SECRETS_ENCRYPTION_KEY` and the former key as `APP_SECRETS_PREVIOUS_ENCRYPTION_KEY`, then restart. The migration rewraps stored credentials with the new key. Remove the previous key after successful migration. Retain the matching keys for database snapshots that still contain older ciphertext. Losing all matching wrapping keys makes those stored credentials unrecoverable.

Application JSON exports use backup format v2 and exclude API/storage credentials, ciphertext, wrapping keys, and password hashes. They also omit extracted chunks/vectors, original objects, and some account/runtime state, and there is no import/restore endpoint. Use database and object backups for full recovery, retaining the matching wrapping keys separately. Database-level backups still contain encrypted credential fields and extracted document content. See [backup contents and recovery limits](database-layout.md#backups-and-recovery).

Application access passwords use bcrypt at cost 12 with a new random salt for every encoding. The salt is embedded in the hash; there is no plaintext-password or separate-salt column. Registration, admin creation, and seed creation use the same password encoder. Login can upgrade older bcrypt cost settings. New passwords are limited to 72 UTF-8 bytes to avoid bcrypt truncation; regular admin-created passwords also require at least eight characters. Password hashes and provider keys are excluded from entity JSON/debug strings. Production cookies use Secure, HttpOnly, and SameSite=Lax under the HTTPS deployment.

Security references: [OWASP password storage](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html), [OWASP cryptographic storage](https://cheatsheetseries.owasp.org/cheatsheets/Cryptographic_Storage_Cheat_Sheet.html).
