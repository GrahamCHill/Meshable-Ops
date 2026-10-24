# Document templates, generated PDFs, and workflows

The app integrates the local WeasyCanvas editor/designer contract v0.8.0 and its matching Python model/WeasyPrint renderer. The integration is vendored in this repository; deploying it does not require a neighbouring WeasyCanvas checkout. The source project is left unchanged. This is an initial document-generation and process-design feature, suitable for extending later.

## Template authoring in Admin

Open **Admin → Document templates**. Use **New template**, **Start with quote**, or **Start with invoice**. The WeasyCanvas designer provides flowing document sections, typography, paper/margins, embedded raster images, repeated headers/footers, and inline/application-owned data fields. The host styles remain scoped and minimal. Admin navigation retains its back link and card-based menu.

Templates are server records with a category, published flag, source JSON, field rules, and optimistic revision. Drafts are visible to admins; published templates are available to authenticated users. Unpublishing prevents new generation by regular users, while existing generated PDFs remain independent snapshots. Saving validates the template through the matching Python schema. Required electronic signatures and externally stored image keys are not supported in this first host integration; optional signature lines and embedded PNG/JPEG/WebP logos remain available.

**Import template** accepts WeasyCanvas JSON under 1.9 MB including images. Host JSON export adds `hostFieldRules` for input types/calculations; this wrapper property is removed before passing the schema to WeasyCanvas. Source-level designer changes stay in memory until Save. They are not stored in browser local storage. Refreshing or choosing another draft can discard unsaved changes.

## Data-driven fields and calculations

Define fields using the designer's **Data sources**, **Inline data fields**, or **data field block** panels. A field has a literal key, label, required flag, maximum length, and optional fallback. Insert a marker such as `{{api:customer.name}}` inside template wording or bind an existing section/heading. Values are supplied by the creation form or authenticated API; the template does not fetch arbitrary URLs or evaluate scripts.

Open **Data field types and calculations** above the designer to choose text, multiline text, number, money, date, email, or calculated value. Calculations reference two declared numeric/calculated fields and support `ADD`, `SUBTRACT`, `MULTIPLY`, `DIVIDE`, and `PERCENT` (first value × second value ÷ 100). The backend uses decimal arithmetic and rounds calculated values/money to two decimal places, half up. Division by zero, cyclic/unknown references, malformed input, and unsupported ranges are rejected. A client-supplied calculated total is ignored and recomputed.

Dates use ISO `YYYY-MM-DD`; numbers support at most 15 whole and six fractional digits. Field keys are literal names, including dots, with reserved prototype keys rejected. Required values and template/field length limits still apply. Quote/invoice starters include organisation, number, customer, date, multiline items, subtotal, tax rate, calculated tax, and calculated total. Items and descriptions are plain multiline text in this first version; line-item tables, automatic numbering, accounting/tax rules, and complex expression languages are future work. Review all business amounts before sending.

## Generating and emailing documents

Open **Documents**, choose a template, enter values and a document name, then select **Generate PDF**. The backend uses the saved template and validated values, not a client-supplied replacement document layout. The result is an immutable PDF with template ID/revision, source snapshot, resolved values, page count, size, and timestamp. The library lists the latest 100 available PDFs. Regular users see only their own documents; admins can manage all. The list uses a metadata-only database projection rather than loading every PDF.

Open PDF uses authenticated raster page previews. Download PDF and Email PDF follow the existing [document policy](document-access.md): disabled downloads prevent both downloads and email; watermark mode adds the configured watermark/user/time before delivery. Generation and previews are independent of download permission. Generated PDFs are currently stored as PostgreSQL `bytea`, not uploaded automatically to S3/Blob or indexed for search. Source storage objects and MongoDB remain unchanged. Back up PostgreSQL for these documents; the existing JSON export omits them.

**Email PDF** attaches one actual PDF to the configured [email sender](email-delivery.md). Choose **PDF without a password** or **Password-protected PDF**. Protected mode uses AES-256 PDF encryption and a password of at least 12 characters, at most 72 UTF-8 bytes. It is supplied only for that send, is not saved in a workflow/database, and is not automatically put in the email. Communicate it separately. The generated library original remains unchanged; an emailed copy cannot be recalled. Maximum generated/protected attachment size is 10 MiB (a provider may impose a lower limit); PDFs are limited to 200 pages. Provider acceptance does not establish inbox delivery, and there is no automatic retry.

Generation is rate limited to 20/account/hour, previews to 120/account/five minutes, and document emails to 10/account/five minutes. Account/document access and policy are checked around processing. No public URL exposes generated originals.

## Building workflows

Open **Workflows**, or use **Attach to workflow** beside a generated PDF. Pick a saved workflow or create a new one, name it, and select library PDFs in its attachments list. A PDF carried from the library is added to the selected workflow's attachments. Add, drag, select, and configure blocks. Drag a connection dot to another block to connect them; select a connection and press Delete to remove it. **Arrange blocks** produces a basic layered layout; text buttons provide fit and zoom.

| Block | Initial configuration |
| --- | --- |
| Start | One entry point for a ready diagram |
| Document | A generated PDF from the workflow attachments |
| Review | Reviewer/approval notes; no automatic assignment yet |
| Condition | Literal data-field key, comparison operator/value, true and false outputs |
| Email | One attached PDF, recipient, subject, message notes, plain/password-protected preference |
| End | A terminal point; multiple ends are allowed |

Save draft allows incomplete diagrams. **Validate & mark ready** requires one start, an end, connected/reachable blocks, an attached PDF for document/email blocks, email recipient/subject, and both outputs for each condition. IDs/coordinates/limits and document ownership are checked on every save, including drafts. Loops and duplicate output connections are rejected. Limits: 50 blocks, 100 connections, and 50 unique attached documents. Changes use optimistic revisions to prevent silent concurrent overwrites. Workflows can be archived; regular users see/edit only their own, while admins can manage all.

**Ready is a design-validation state, not a running workflow.** This version saves diagrams and attachments; it does not execute conditions, assign reviews, schedule actions, or send emails automatically. **Prepare email** on an email block opens the explicit PDF send form, using its selected document and saved defaults. Passwords are entered at send time and never stored on blocks. A future execution engine can build on these typed blocks and validated graphs without interpreting arbitrary code.

## Renderer deployment and security

Docker Compose includes `weasy-renderer`, built from `weasy-renderer/Dockerfile`. It installs WeasyPrint/Pango/fonts and exposes port 8000 only to the Compose network. It runs as a non-root user with a read-only root filesystem, temporary `/tmp`, dropped capabilities, no privilege escalation, and bounded memory/process limits. Ordinary app startup/search/AI do not require successful rendering; missing/unavailable rendering returns a safe 503 for the document operation.

`APP_DOCUMENT_RENDER_URL` defaults to `http://weasy-renderer:8000`. Native backend development can use `http://127.0.0.1:8720`. Set the same `APP_DOCUMENT_RENDER_TOKEN` on backend and renderer, especially if running outside the private Compose network. A configured token is checked with constant-time comparison and is never sent to the browser. Keep the service private; a blank token relies on network isolation. The backend does not follow renderer redirects.

The renderer accepts the structured WeasyCanvas schema, not arbitrary HTML/CSS. Source/field text is escaped. External network/file/SVG resource URLs and external asset keys are blocked; only bounded embedded raster images are accepted. Each PDF runs in a child process with a 45-second wall-clock timeout and Linux memory/CPU limits. At most two jobs run concurrently. Input is bounded to 2 MiB; output is bounded to 10 MiB and 200 pages. Health reports service availability; generate a sample document to verify native libraries and fonts. See [WeasyPrint's security guidance](https://doc.courtbouillon.org/weasyprint/stable/first_steps.html#security).

The frontend designer, backend services, and matching renderer sources are documented in [code organization](code-organization.md). [API contracts](api.md#document-templates-and-generated-documents) and [database layout](database-layout.md#document-generation-and-workflow-tables) describe integration points. No credentials, passwords, source snapshots, workflow notes, or generated PDFs become document-search/AI sources automatically.
