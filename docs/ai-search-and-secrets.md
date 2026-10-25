# AI search and credential security

## Start with search only

Storage search and the chat launcher default to enabled. The floating assistant is available to members with search access; it offers search and setup guidance while AI answers or content indexing are disabled or a chat model is absent. AI answers and content indexing default to disabled. Leave embeddings on **Internal text matcher** to search metadata with no AI credentials, model download, or ML container. Add/share storage folders and reindex them; the database starts empty, so add your own folders and original documents.

1. For local Compose, run `python deploy/github/setup-instance.py --local --admin-email owner@example.com`. For production, use the [deployment guide](deployment.md). Blank environment examples contain no working credentials.
2. Configure a persistent wrapping key before saving credentials or running the MinIO/Azurite development profiles. Generate it once with `openssl rand -base64 32`, set `APP_SECRETS_ENCRYPTION_KEY`, and keep a separate secure copy. Development storage seeds also save keys and need this value even when AI is off.
3. Rebuild/start the stack, configure accessible folders, and run **Reindex now** under **Administration > Storage connections > Search index**.
4. Use the ordinary search field in **Storage** or open the bottom-right **Workspace assistant**. The assistant supports search immediately and switches to **Ask AI** once AI answers, content indexing, and a chat model are configured. **App Intelligence** independently controls search, launcher visibility, AI answers, and content indexing.

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
   Enter an API URL and key (or retain a saved key) to load available models automatically. Select a model from the list, or enter a custom model/deployment name if discovery is unavailable. Local Ollama can list installed models without a key. **Refresh models** queries the provider again.
3. Enable **Index document content** and **Enable AI answers**. Save and test the saved chat/embedding configuration; these checks send fixed test text, not documents.
4. Reindex after enabling content indexing or changing the embedding provider/model. **Ask AI** retrieves relevant accessible excerpts and returns numbered source links into Storage.

No AI configuration or an unavailable provider leaves ordinary search available. Chat failures return a non-grounded guidance response; embedding failures use access-checked keyword matches for ordinary search. Disabling search explicitly rejects search and chat requests. See [feature controls and indexing jobs](search-and-indexing.md).

**App Intelligence** checks the selected model automatically before saving, using only short fixed test text. Chat is verified against the app's chat adapter. Embeddings are confirmed by the returned vector's actual length and validity, rather than by the model name or its presence in a provider catalogue. If the current dimensions setting fails or returns a different length, the check tries the other setting once. **Apply suggested dimensions setting** enables or disables the request parameter when that produces 1536 values. Models returning another size are marked incompatible; authentication, quota, and provider outages leave compatibility unverified. **Check selected model** retries the check. A successful check confirms the tested request at that time, not future provider availability.

OpenAI-compatible providers, Groq, Ollama, and Azure use their `/models` endpoint; Anthropic uses its authenticated Models API with bounded pagination. Model catalogues may include models for other tasks. Azure model IDs can differ from deployment names: enter the deployed name and check it before saving. Providers without model discovery still support manual entry. Discovery and checks use the selected configuration source and reuse a saved key only for the same provider/URL; they never persist a draft key or expose it in a response.

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

The current PostgreSQL schema uses `vector(1536)`. External embedding models must return 1536 finite, nonzero values. Enable the dimensions parameter for models that support it. For Ollama, select a model with native 1536-dimensional embeddings or one that supports requesting 1536 dimensions. Incompatible responses fail explicitly; vectors are never silently truncated or replaced by random vectors.

1536 matches the native output of OpenAI's `text-embedding-3-small`; `text-embedding-3-large` can also request 1536 dimensions ([embedding dimensions](https://developers.openai.com/api/docs/guides/embeddings)). It stays within pgvector's 2000-dimension limit for full-precision HNSW indexes ([pgvector HNSW](https://github.com/pgvector/pgvector#hnsw)). Increasing dimensions alone does not turn the internal feature matcher into a semantic model. Vector storage grows roughly fourfold compared with 384 dimensions, and ranking/index construction may take longer.

When upgrading an existing database, startup migrates the host-owned vector columns to 1536 dimensions in a transaction. Old storage vectors become NULL and receive an incompatible model identity; document metadata, extracted chunks, and originals remain available. Run **Reindex now** to generate new storage vectors. Local Kanban and installed ZIP-plugin vectors are regenerated automatically from their saved search text during the migration.

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
AI_EMBEDDING_MODEL=local-feature-hash-v3
AI_EMBEDDING_API_KEY=
AI_EMBEDDING_SEND_DIMENSIONS=true
```

Environment keys remain in the deployment environment. They are not copied into the database by AI settings. Admin-entered keys use the encrypted fields described below. Selecting the environment source in Admin makes the environment configuration active; saving an admin configuration selects that separate configuration.

## Indexing and access

Original files stay in the selected S3 bucket or Azure Blob container. PostgreSQL stores their folder/document identity, processing status, metadata vectors, and extracted text chunks. Upload, discovery, or explicit/scheduled reindexing updates the index. A full reindex also removes documents no longer present in storage.

The internal helper extracts existing PDF text layers, DOCX paragraphs, and UTF-8 formats. Scans use an optional external OCR adapter; no OCR engine is bundled. Native extraction is bounded to 50 MiB by default, 65 seconds, 2 million characters, and 500 PDF pages. Background indexing retains page provenance and supports up to 4,096 chunks in provider batches of 32. See [processing and adapter contract](document-processing-extension.md).

Changes are detected using object path, name, content type, size, and modification time. Content chunks additionally store a SHA-256 hash of the downloaded bytes. If a provider changes bytes without changing that metadata, run a full reindex. The embedding provider, endpoint, model, dimension settings, and local algorithm version form an index identity. Queries only use matching vectors, including during provider/model changes. Reindex after changing embeddings or enabling content indexing. Disabling content indexing also excludes existing chunks from search immediately.

Search SQL joins live folder visibility and connection state for folder vectors, document vectors, and content chunks. AI answers only use relevant content chunks, never filename metadata as document evidence. Access is rechecked before calling the model and again before returning the answer. Revoking a folder or connection during generation suppresses the entire answer. Source links resolve the folder through the regular authenticated storage APIs, which enforce the same boundaries.

Content work runs in durable PostgreSQL jobs with bounded retries. Failures retain metadata search and report CONTENT_RETRY or CONTENT_FAILED; scans without a configured OCR adapter report OCR_REQUIRED. Reindex to queue another attempt after changing configuration. Events and audit records remain excluded from document search. See [document processing](document-processing-extension.md).

## Stored credentials and passwords

API keys and both storage credential fields use AES-256-GCM at rest with a fresh 96-bit nonce and authentication tag per write. The wrapping key is a base64-encoded 32-byte server secret, separate from PostgreSQL and JWT signing. JPA converters encrypt writes and decrypt server-side reads. Stored ciphertext has a version and key identifier; malformed or unauthentic ciphertext fails closed. Ordinary reads do not accept plaintext; only the startup migration handles legacy plaintext rows.

Email settings use the same converter and rotation process for SMTP usernames, passwords/app passwords/OAuth2 tokens, and email service API keys. These are recoverable outbound-service credentials; application access passwords remain separately hashed. Email settings responses expose configured flags instead of credentials. See [email delivery](email-delivery.md).

For rotation, configure a new `APP_SECRETS_ENCRYPTION_KEY` and the former key as `APP_SECRETS_PREVIOUS_ENCRYPTION_KEY`, then restart. The migration rewraps stored credentials with the new key. Remove the previous key after successful migration. Retain the matching keys for database snapshots that still contain older ciphertext. Losing all matching wrapping keys makes those stored credentials unrecoverable.

Application JSON exports use backup format v2 and exclude API/storage credentials, ciphertext, wrapping keys, and password hashes. They also omit extracted chunks/vectors, original objects, and some account/runtime state, and there is no import/restore endpoint. Use database and object backups for full recovery, retaining the matching wrapping keys separately. Database-level backups still contain encrypted credential fields and extracted document content. See [backup contents and recovery limits](database-layout.md#backups-and-recovery).

Application access passwords use bcrypt at cost 12 with a new random salt for every encoding. The salt is embedded in the hash; there is no plaintext-password or separate-salt column. Registration, admin creation, and explicit account bootstrap use the same password encoder. Login can upgrade older bcrypt cost settings. New passwords are limited to 72 UTF-8 bytes to avoid bcrypt truncation; regular admin-created passwords also require at least eight characters. Password hashes and provider keys are excluded from entity JSON/debug strings. Production cookies use Secure, HttpOnly, and SameSite=Lax under the HTTPS deployment.

Security references: [OWASP password storage](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html), [OWASP cryptographic storage](https://cheatsheetseries.owasp.org/cheatsheets/Cryptographic_Storage_Cheat_Sheet.html).
