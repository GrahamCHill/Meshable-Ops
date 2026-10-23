# PDF viewing and downloads

Open **Administration > Document access** from its admin card. The existing `ADMIN` role controls these global settings, which apply to administrators as well as regular users. Every section retains **Back to admin menu**.

| Setting | Behavior |
| --- | --- |
| Allow PDFs to open in Storage | Shows **Open PDF** and permits authenticated PDF preview metadata/page requests |
| Download normally | Downloads original files and normal folder ZIPs |
| Download with watermarks | Creates watermarked PDF copies; other file formats download normally; PDFs in folder ZIPs are stamped individually |
| Downloads disabled | Hides all file/folder download controls and rejects direct object/ZIP download API requests |

PDF viewing and downloads are independent. For example, keep viewing on and disable downloads to allow a reader without exposing the original PDF through a browser PDF toolbar. The reader provides previous/next page navigation and displays rendered page images. It never loads the raw original into an iframe. UI policy refreshes about every 30 seconds; saving in the same browser updates immediately. Backend checks apply immediately to new requests and recheck before returning processed output.

Watermarks contain the configured text (initially **CONFIDENTIAL**), signed-in email, and server UTC timestamp on every page. Stored originals remain unchanged. Generated copies may invalidate digital signatures; they are derivative copies, not replacements for signed originals. Unsupported font glyphs are replaced with `?` so the remaining identity/text remains visible. Filename, content type, and PDF header detection prevent a renamed PDF being treated as an ordinary non-PDF download. A failed PDF transformation aborts the file or entire ZIP response; it never falls back to unwatermarked bytes. An already-compressed ZIP stored as an object is downloaded as that original object; nested archives are not recursively inspected or rewritten.

Previewing and watermarking accept unencrypted PDFs up to **25 MB**, **200 pages**, and bounded page dimensions. Individual rendered pages fit within a 1800 by 2400 pixel box before rotation. Two PDF jobs may process concurrently per backend instance; excess requests return a retry message. Damaged, encrypted, oversized, or unsupported PDFs fail with a safe error. Normal original downloads are independent of these PDF processing limits. Watermarked ZIPs fail if any contained PDF exceeds those limits.

The backend uses PDFBox and headless Java. The runtime image installs fontconfig/DejaVu for rendering and uses PDFBox's bundled Liberation Sans for watermark text. PDF viewing and watermarking do not require AI, embeddings, the optional ML extraction service, or MongoDB. Preview/download responses use `Cache-Control: no-store`; sharing and policy are rechecked before delivery. Page images can still be saved or captured by someone allowed to view them; these controls do not prevent screenshots.

Defaults preserve current access: PDF viewing is on, downloads are normal, watermark text is **CONFIDENTIAL**. Settings are persisted in PostgreSQL `document_access_settings` on first admin save. Back up PostgreSQL normally; the existing JSON export does not include this policy. See [API contracts](api.md#pdf-viewing-and-download-policy) and [Storage search](search-and-indexing.md).
