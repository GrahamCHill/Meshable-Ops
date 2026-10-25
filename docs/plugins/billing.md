# Invoices, quotes and billable items

Enable `documents` and `billing`. Optionally enable `billing-items`, `timesheets` and `cloud-costs`. The billing plugin uses project/client RBAC, writes generated PDFs into the document library, and adds client/project extension links. Billing authoring requires a project leader/administrator or the client's explicitly assigned Business Manager who can access the project.

## Business and client setup

Under **Administration → Operations → Invoices & quotes**, configure the issuing business's name/address, default currency, VAT registration/number and default VAT percentage. **USD is the fallback**; ISO currencies can be selected per client. Tax is not automatically enabled. The company must be marked VAT registered before an invoice can charge VAT.

Each client has a registered billing email/address, internal Business Manager (or administrator), payment terms, currency and optional client tax number. A client owner or administrator configures this in **Invoices & quotes → Client billing** or through the client extension. Project billing requires a real linked client; application-wide contact defaults are not used. Client delivery goes only to that registered billing email after a human signs off the generated document.

Stored catalog/manual prices explicitly record **includes tax** or **excludes tax**. For VAT-enabled invoices, inclusive prices are converted to net prices, and the PDF shows net line/unit values plus a separate VAT column/total. Exclusive prices have VAT added. Without VAT, no tax is inferred or stripped from stored amounts. Currency precision and per-line rounding are applied before totals are summed. The administrator chooses rates appropriate to the business; the app does not determine tax jurisdiction.

## Shared catalog and local agreements

Billing items are a shared catalog, independent of projects. Administrators, Business Managers and project leaders can create definitions; the owner or an administrator can edit them. Link an item to a client or a project. A link snapshots its agreed values so catalog edits do not silently change a price. Items and rates have an enabled/disabled switch, with no recurrence or active-date fields. Disabling a catalog item stops its linked charges; price snapshots remain intact.

A local override is a proposal. The existing baseline or previously approved override remains effective until a project **MANAGER** or administrator approves the exact proposal version. Team lead status alone cannot approve. Client-wide proposals require approval separately for each affected project. Project links take precedence over inherited client links. Relinking a new catalog version cannot bypass an inherited agreement. No pending value enters an invoice. A client-wide monthly item uses one shared charge key across the client's projects, preventing the same charge being billed once per project.

Use **Billing items & rates > Project links / Client links** to link definitions and propose changes; project managers approve from the project view. Editing a catalog price does not require project approval because it does not alter an existing agreed price. Existing project-scoped records remain readable for migration compatibility.

## Project billing and member rates

Open a project and select **Billing**. **Items & individuals** links billable catalog items and assigns each internal project member a going hourly rate. A project member rate takes precedence over a default billing-role assignment. Disabling a project member rate prevents new billable approvals for that member rather than falling back to a default. Default roles and member assignments also have enabled switches. Existing approved time retains its rate snapshot.

The reported timesheet link opens team review. Submit and approve billable work before calculating charges under **Invoices & quotes**. Charges are approved seconds ÷ 3600 × the approved hourly rate. Missing rates stop approval when Billing items & rates is enabled. Generating a PDF creates a draft: download and review it, then choose **Sign off reviewed PDF**. Sign-off records the actor and timestamp against that exact document revision. The server rejects delivery without sign-off, including direct API requests and scheduled documents.

**Client timesheet export** downloads a CSV of approved billable project entries for a selected period of up to 93 days, including member names, UTC times, descriptions, hours, rate currency and amounts. Detailed team-review authorization applies; spreadsheet formula prefixes and CSV quoting are escaped. Downloading does not send anything to the client.

## Payment provider plugins

Open **Billing → Payment providers** in a project or **Invoices & quotes → Payment providers**. Administrators can enable provider plugins and open each plugin's configuration here. Choose a default payment method for new documents, or choose another configured method while generating an individual draft. Invoice PDFs snapshot the payment instructions, so later settings changes do not alter an existing invoice. The optional cover field `payment_instructions` is also available.

Bundled provider plugins are `payment-bank-transfer` (bank, account holder, account/IBAN, branch/routing/SWIFT and instructions), `payment-payfast` (a provider-supplied HTTPS hosted payment link), and `payment-hosted` (another provider's name, HTTPS payment link and instructions). Each depends on Billing and is independently enabled/configured. Reviewed backend modules can add a `BillingPaymentProvider` bean and plugin manifest. Hosted links take clients to the provider; the app does not create checkout sessions or automatically reconcile payment callbacks. Confirm receipt before marking an invoice paid. Quotes and provisional PDFs omit payable instructions.

## Sources and duplicate protection

| Source | Invoice key / behavior |
| --- | --- |
| Enabled linked flat item, e.g. hosting | Stable item + calendar-month key; one full charge per month touched by the period; **no automatic proration** |
| Approved work | Stable ledger-entry key, approved rate snapshot × recorded seconds / 3600 |
| Optional cloud costs | Stable connection + date + service + currency cost key; current provider snapshot |
| Manual line | Explicit ad hoc line scoped to the generation request |

Preview the period and review its line items. Invoice generation reserves source keys atomically and locks billable work. Concurrent/replayed generation cannot silently create duplicate charge items. A reused generation request ID returns the existing document. Already-reserved sources block a second invoice unless a human types **BILL AGAIN** and records a reason of at least ten characters. The reason is saved with the document. Scheduled runs never supply that confirmation.

**Quotes and provisional statements are not payable**, carry a visible watermark, and do not reserve or consume sources. Use provisional statements for a review of services so far. A final invoice can then include those services once. Voiding an invoice retains source reservations; deliberate rebilling still requires explicit confirmation. Paid/void documents cannot be resent through the normal send action. The app records “paid” manually and does not process a payment.

Only enabled supporting plugins contribute sources. All selected sources must use the client invoice currency. Missing approved rate snapshots and mixed currencies stop generation for review. Unapproved/nonbillable hours are excluded. Work is allocated by UTC start date. Reviewed cloud adjustments after billing do not rewrite the original PDF; use a separately reviewed adjustment process.

## PDFs, templates and delivery

The standard escaped invoice layout is rendered by the private Python WeasyPrint service. It supports multiple pages, repeating column headings and net/VAT totals. An optional published WeasyCanvas template adds a custom cover before the complete itemized layout. The fixed itemized pages always retain net prices and VAT totals. Template fields are `invoice_number`, `document_kind`, `client_name`, `billing_email`, `billing_address`, `project_name`, `period_from`, `period_to`, `currency`, `subtotal`, `vat`, `total`, and `invoice_lines` (a bounded multiline preview; full details follow on the itemized pages). Configure required template fields accordingly. Quotes/provisional outputs are watermarked regardless of layout.

Generated PDFs appear in Documents for their owner and administrators, with project-authorized download/delivery through Billing. Download disable/watermark rules still apply. Clearing Documents is blocked while saved billing documents reference its PDFs. PDF attachments are capped at 10 MB. Normal manual sends may optionally password-protect the attachment; passwords are used in memory and are never emailed alongside it or stored.

**The PDF is attached to the email.** Delivery uses the existing notification/email system and `billing.document` templates/settings. The recipient is fixed to the registered billing address captured in the document; a changed address requires a new document. Provider acceptance is recorded as SENT, not guaranteed inbox delivery. Uncertain attempts require checking the provider and typing **RETRY DELIVERY** before resending. A crashed SENDING attempt remains held for operator review rather than automatically sending another copy.

Monthly schedules default to **disabled, draft only**. Choose day 1–28, local time/timezone, VAT options, and whether to include cloud costs. Runs cover the previous full month, retain owner authorization checks, and record status. Jobs are claimed in PostgreSQL across app instances. Scheduled runs always generate drafts awaiting sign-off; legacy auto-send preferences are ignored. Sign off and send each draft explicitly after review. Configure a mail route before sending. Password-protected delivery is an explicit manual action.

## Reading billing replies

Configure mail connections under **Administration → Communications**, rather than in the invoice plugin's business settings. **Outgoing email (SMTP / API)** sends invoices; **Incoming email (IMAPS)** reads replies. Sending needs no IMAPS connection. Incoming and outgoing server, port and login details are independent; see [email configuration](../email-delivery.md#outgoing-smtp-versus-incoming-imaps).

Create and enable a shared IMAPS profile for a dedicated mailbox or folder, then open **Communications → Invoice email replies**, select that profile and enable **Process invoice replies**. The outbound email's Reply-to must deliver into this inbox. Both switches must be enabled and the connection complete before polling can run. Existing Billing inbox credentials migrate automatically into a shared profile without losing the processing switch or business configuration. Credentials use TLS with normal certificate/hostname verification and encrypted storage. A durable UID cursor drains the mailbox in bounded batches and preserves messages; it never marks/deletes mail. Attachments and remote HTML are not opened.

Each outgoing subject includes an unguessable document correlation reference. A reply must retain that reference and have the document's registered billing sender address. The app routes its bounded plain-text content to the client's current Business Manager and project lead using their enabled notification preferences. Cancellation requests receive a label for human review; **no automatic cancellation, credit or service shutdown occurs**. Reply bodies are encrypted at rest. Sender matching is correlation, not cryptographic proof of sender identity; retain normal mailbox anti-spoofing protections. Messages without the reference or from a different address remain in the mailbox for manual handling.

No inbox access, real mail sends or cloud billing queries occur merely by saving configuration or enabling the page. Only explicit sends, polls/refreshes or separately enabled schedules contact those services.

Enabled linked items use calendar-month charge keys and no automatic proration; use manual lines for ad hoc one-off charges.
