# Document templates, generated PDFs, and workflows

The app integrates the local WeasyCanvas editor/designer contract v0.8.0 and its matching Python model/WeasyPrint renderer. The integration is vendored in this repository; deploying it does not require a neighbouring WeasyCanvas checkout. The source project is left unchanged. Admins design templates and processes; members generate PDFs and follow published workflows with saved progress.

## Template authoring in Admin

Open **Admin → Document templates**. Use **New template** to start with a blank document, then define your own content, fields, and layout. There are no built-in business document types or required financial fields. The WeasyCanvas designer provides flowing document sections, typography, paper/margins, embedded raster images, repeated headers/footers, and inline/application-owned data fields. The host styles remain scoped and minimal. Admin navigation retains its back link and card-based menu.

Templates are server records with a name, published flag, source JSON, field rules, and optimistic revision. The API retains optional category metadata for compatibility; it does not control the editor or document generation. Drafts are visible to admins; published templates are available to authenticated users. Unpublishing prevents new generation by regular users, while existing generated PDFs remain independent snapshots. Saving validates the template through the matching Python schema. Required electronic signatures and externally stored image keys are not supported in this first host integration; optional signature lines and embedded PNG/JPEG/WebP logos remain available.

**Import template** accepts WeasyCanvas JSON under 1.9 MB including images. Host JSON export adds `hostFieldRules` for input types/calculations; this wrapper property is removed before passing the schema to WeasyCanvas. Source-level designer changes stay in memory until Save. They are not stored in browser local storage. Refreshing or choosing another draft can discard unsaved changes.

## Data-driven fields and calculations

Define fields using the designer's **Data sources**, **Inline data fields**, or **data field block** panels. A field has a literal key, label, required flag, maximum length, and optional fallback. Insert a marker such as `{{api:customer.name}}` inside template wording or bind an existing section/heading. Values are supplied by the creation form or authenticated API; the template does not fetch arbitrary URLs or evaluate scripts.

Open **Data field types and calculations** above the designer to choose text, multiline text, number, money, date, email, or calculated value. Calculations reference two declared numeric/calculated fields and support `ADD`, `SUBTRACT`, `MULTIPLY`, `DIVIDE`, and `PERCENT` (first value × second value ÷ 100). The backend uses decimal arithmetic and rounds calculated values/money to two decimal places, half up. Division by zero, cyclic/unknown references, malformed input, and unsupported ranges are rejected. A client-supplied calculated total is ignored and recomputed.

Dates use ISO `YYYY-MM-DD`; numbers support at most 15 whole and six fractional digits. Field keys are literal names, including dots, with reserved prototype keys rejected. Required values and template/field length limits still apply. Content, named fields, and calculation rules are defined per template; none are added automatically. Multiline text and numeric calculations are available when needed. Line-item tables, automatic numbering, and complex expression languages are future work.

## Generating and emailing documents

Open **Documents**, choose a template, enter values and a document name, then select **Generate PDF**. The backend uses the saved template and validated values, not a client-supplied replacement document layout. The result is an immutable PDF with template ID/revision, source snapshot, resolved values, page count, size, and timestamp. The library lists the latest 100 available PDFs. Regular users see only their own documents; admins can manage all. The list uses a metadata-only database projection rather than loading every PDF.

Open PDF uses authenticated raster page previews. Download PDF and Email PDF follow the existing [document policy](document-access.md): disabled downloads prevent both downloads and email; watermark mode adds the configured watermark/user/time before delivery. Generation and previews are independent of download permission. Generated PDFs are currently stored as PostgreSQL `bytea`, not uploaded automatically to S3/Blob or indexed for search. Source storage objects and MongoDB remain unchanged. Back up PostgreSQL for these documents; the existing JSON export omits them.

**Email PDF** attaches one actual PDF to the configured [email sender](email-delivery.md). Choose **PDF without a password** or **Password-protected PDF**. Protected mode uses AES-256 PDF encryption and a password of at least 12 characters, at most 72 UTF-8 bytes. It is supplied only for that send, is not saved in a workflow/database, and is not automatically put in the email. Communicate it separately. The generated library original remains unchanged; an emailed copy cannot be recalled. Maximum generated/protected attachment size is 10 MiB (a provider may impose a lower limit); PDFs are limited to 200 pages. Provider acceptance does not establish inbox delivery, and there is no automatic retry.

Generation is rate limited to 20/account/hour, previews to 120/account/five minutes, and document emails to 10/account/five minutes. Account/document access and policy are checked around processing. No public URL exposes generated originals.

## Building workflows

Open **Administration > Workflow builder** (`/admin/workflows`), or **Attach to workflow design** beside a generated PDF as an admin. The main sidebar's **Workflows** entry opens the guided user experience. Admins can manage existing designs, including those previously created by members. Each admin section retains **Back to admin menu**.

Name a design, select library PDFs in its attachments list, then add, drag, select, and configure blocks. Connect output dots to other blocks; select a connection and press Delete to remove it. **Arrange blocks** gives a basic layered layout; text buttons provide fit and zoom.

| Block | Configuration and member action |
| --- | --- |
| Start | One entry point; member selects Begin process |
| Document | Attached generated PDF; preview/download according to document policy, then Continue |
| Review | Instructions and an explicit review checkbox, with optional saved note; no reviewer assignment or separate approver enforcement |
| Condition | Literal data-field key, comparison operator/value, True and False connections; member enters the value and server chooses the branch |
| Email | Attached PDF, fixed recipient/subject/message and plain/password preference; member explicitly sends or records delivery handled separately |
| End | Member selects Finish workflow; multiple ends are allowed |

**Save draft** allows incomplete diagrams. **Validate & mark ready** requires one start, an end, reachable blocks, complete paths, attached PDFs for document/email blocks, email recipient/subject, and both condition outputs. IDs, positions, limits, and document access are checked on every save, including drafts. Loops and duplicate outputs are rejected. Limits: 50 blocks, 100 connections, 50 unique attached PDFs. Optimistic revisions prevent silent concurrent overwrites.

Ready designs remain private until an admin selects **Publish for members when saved as ready** and saves. Existing ready designs default to unpublished on upgrade. Publishing is an explicit grant to internal members: PDFs configured on document/email steps become available through their private run only as those steps are reached. This does not expose the admin's document library or documents on unvisited branches. Saving a draft, archiving, or unpublishing removes a design from the member catalogue and pauses existing member runs and their PDF access. Admins can preview unpublished ready designs; drafts/archives cannot be run.

## Following a workflow

Open **Workflows** (`/workflows`), start a published process, and follow its current step. **Your runs** lists the latest 100 runs for the signed-in member, including completed and cancelled runs. Reloading or returning later resumes server-saved progress. Runs are private to their owner, including against other admins. Each start captures the design and its revision; later edits apply to new runs. Paused runs retain their history and can be cancelled. A cancelled run cannot resume. Starting is limited to 20/account/hour.

Conditions evaluate literal input, not code or automatic queries against document fields. String equality trims whitespace and is case sensitive; Contains is case sensitive; Is set checks for nonblank input. Greater than uses decimal numbers with up to 30 whole and 12 fractional digits. Values are saved in the run. The server enforces current node, revision, action, and next branch; users cannot skip directly to a future step.

At a document/email step, members may select a generated PDF already available in their own document library. It replaces that PDF throughout their run, including later steps referring to the same original document. It does not edit the design, other runs, or the stored PDF. The library's **Use in workflow** link carries a suggested PDF; members still explicitly select it at a suitable step. Preview, download, watermark, and attachment policies remain enforced on run-specific PDF endpoints. Previously reached document steps remain accessible while the workflow remains published/ready.

Email never executes in the background. **Send PDF & continue** uses the saved recipient, subject, message, protection preference, and current PDF. Password-protected sends ask for a password at send time; it is not persisted in the design/run/history or included automatically in the message. The PDF is an email attachment. Provider acceptance advances the step but does not prove inbox delivery. Missing or disabled email notification delivery leaves manual confirmation available and does not fail the rest of the workflow system. The Workflow email steps notification controls the explicit send; separate opt-in workflow alerts follow member preferences. See [Notifications](notifications.md).

A durable claim prevents duplicate sends. If a send times out or its result is uncertain, the app will not automatically resend it. Check the provider/outbox, then explicitly record how delivery was handled; a note is required. An in-flight send blocks manual confirmation/cancellation for two minutes, after which a stuck attempt can be handled manually. Manual confirmation is also available when delivery was handled outside the app. Reviews and delivery confirmations are member acknowledgments, not a multi-person approval system. Scheduling, assignments, parallel execution, and background automation are not implemented.

## Renderer deployment and security

Docker Compose includes `weasy-renderer`, built from `weasy-renderer/Dockerfile`. It installs WeasyPrint/Pango/fonts and exposes port 8000 only to the Compose network. It runs as a non-root user with a read-only root filesystem, temporary `/tmp`, dropped capabilities, no privilege escalation, and bounded memory/process limits. Ordinary app startup/search/AI do not require successful rendering; missing/unavailable rendering returns a safe 503 for the document operation.

`APP_DOCUMENT_RENDER_URL` defaults to `http://weasy-renderer:8000`. Native backend development can use `http://127.0.0.1:8720`. Set the same `APP_DOCUMENT_RENDER_TOKEN` on backend and renderer, especially if running outside the private Compose network. A configured token is checked with constant-time comparison and is never sent to the browser. Keep the service private; a blank token relies on network isolation. The backend does not follow renderer redirects.

The renderer accepts the structured WeasyCanvas schema, not arbitrary HTML/CSS. Source/field text is escaped. External network/file/SVG resource URLs and external asset keys are blocked; only bounded embedded raster images are accepted. Each PDF runs in a child process with a 45-second wall-clock timeout and Linux memory/CPU limits. At most two jobs run concurrently. Input is bounded to 2 MiB; output is bounded to 10 MiB and 200 pages. Health reports service availability; generate a sample document to verify native libraries and fonts. See [WeasyPrint's security guidance](https://doc.courtbouillon.org/weasyprint/stable/first_steps.html#security).

The frontend designer, backend services, and matching renderer sources are documented in [code organization](code-organization.md). [API contracts](api.md#document-templates-and-generated-documents) and [database layout](database-layout.md#document-generation-and-workflow-tables) describe integration points. No credentials, passwords, source snapshots, workflow notes, or generated PDFs become document-search/AI sources automatically.
