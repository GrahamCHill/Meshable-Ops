# Workflows attached to application actions

Enable Workflows and open **Administration > Workflow builder**. **Diagram & steps**
designs a process; **Action bindings** attaches it to a server action behind a button.
No policies are enabled automatically on upgrade.

## Drag modules into the diagram

The left palette's **Steps** and **Plugins** tabs contain draggable steps and searchable **Plugin triggers**. Drag
a step to an exact canvas position, or click its button to add it. Dragging a plugin
trigger connects and selects the existing Start step, preserving its transitions;
it does not add a second Start. Click is also available for keyboard/touch use.
Select Start to change its action or scope. **Validate & mark ready** with publishing
enabled saves the diagram and its enforced binding in one transaction.

The Action bindings tab identifies diagram-managed policies. Edit these through
the Start inspector. To remove one, select **Manual start only** and publish the
replacement. Drafting or archiving pauses its workflow while retaining the last
enforced policy, so it cannot silently release an action. Existing independent
bindings continue to work alongside a diagram trigger.

## Action bindings

1. Build, validate, and publish a prerequisite workflow.
2. Choose a registered action, plugin area, or **All external deliveries and exports**.
3. Select the workflow and optionally scope it to a board, destination column,
   project, plugin, or resource path. Select **Attach workflow**.

Prerequisites cannot contain Email steps: the pending send happens after the
process finishes. Multiple matching bindings must all complete. This covers
API-backed buttons and direct API calls; browser-only controls such as zoom remain
local. Reviewed React plugins should use the shared application API client; other
clients must handle HTTP 428 and its `runId` before retrying the exact action.

The initial attempt saves a prerequisite run and returns HTTP 428 before the business
controller executes. The browser opens the process and keeps the original request
in memory. **Continue action** explicitly resumes it after completion. Cancelling
discards the pending request but preserves workflow progress. Reloading never
restores private request bodies or PDF passwords.

Authorizations bind the initiator, binding/design revisions, path, method, query
values, and payload fingerprint. All approvals are consumed atomically before the
final attempt, preventing concurrent replay. They expire after 24 hours. Changed
payloads/policies require a new process. Business permissions, ownership, validation,
and source revisions still apply. A failed final attempt consumes approval too:
an uncertain external send must not be automatically repeated.

Action records do not store request bodies, attachments, credentials, or passwords.
An explicit allowlist provides bounded immutable `trigger.*` context for review.
Multipart fingerprints include metadata parts as well as files. Manually streamed
controllers and JSON exceeding the bounded capture require a reviewed payload
adapter before binding; unbound actions retain their existing limits and behavior.
Rule matching uses decoded path segments without matrix parameters, preventing
encoded URL aliases from bypassing a binding. Scalar scope values match in full;
Kanban target-column scopes additionally match individual IDs in a bulk transition.
Context exceeding 60 fields or 500 characters per field rejects the bound action;
it is never silently shortened before a code/condition step. Split large bulk edits
into smaller reviewed actions.

## Review, rejection, and audit

Approval steps offer **Allow the initiating user to approve**. With it off, select
active internal reviewers: the server requires a different assigned person. A forged
acknowledgment cannot override the rule. With it on, the initiator may approve too.

Assigned reviewers use **Workflows > Your review inbox**. They see the current review
and action context, not the owner's future steps, full history, or document library.
They receive a scoped receipt and cannot cancel the owner's run or advance later steps.

**Require an audit note** applies to any block. Audit blocks always require one.
Completed steps record actor, outcome, timestamp, and note. Transitions, designs,
bindings, authorizations, and email attempts also write application audit events.
Run endpoints cannot edit previous visits.

Reviewers can **Reject action** with a reason. End nodes explicitly **Allow** or
**Reject**, so a condition's false branch can deny the action. A rejected run never
authorizes resumption.

## Restricted code modules

Code blocks interpret a bounded Python-style module in the backend:

```python
def run(context):
    amount = number(context["trigger.input.amount"])
    total = amount * 1.15
    if total > 1000:
        return {"total": total, "needs_review": True}
    else:
        return {"total": total, "needs_review": False}
```

A following condition can use **Saved code output or action context**, field
`needs_review`, Equals, `True`. `trigger.*` conditions always use saved context,
ignoring a member's attempted replacement value.

This is a restricted language, not unrestricted CPython. It supports four-space
indentation, assignments, `if`/`else`, dictionary returns, strings, bounded decimals,
booleans, comparisons, `and`/`or`/`not`, and arithmetic. Built-ins: `number`, `text`,
`len`, `min`, `max`. Read values through `context["field"]`; return scalar run values.

Imports, external libraries, files/network/system access, reflection, arbitrary calls,
loops, recursion, comprehensions, and attributes are unavailable. Limits include
8 KiB/64 statements, bounded expressions/nesting/operations, 40 output fields, and
500 characters per output. Scripts cannot overwrite `trigger.*` or prototype keys.
Publishing and execution validate code; drafts can retain incomplete modules.

## Plugin coverage and boundaries

Named modules cover project report generation/PDF export/scheduling, knowledge
page create/update/delete and book export, timesheet creation/review/export, billing
generation/send/state/PDF export, TimeCamp sync, cloud-cost refresh, on-call alert
actions, and CSV/PDF export and snapshot refresh for all five analytics connectors.
Document generation/download/email, calendar create/update/delete/export/provider
publication, Jira project linking/sync and Figma linking/refresh are named modules too.
These contracts match both HTTP method and path. General listing and preview
requests do not activate them. Dates, action/state, and scoped resource IDs are
available as trusted context; passwords, tokens, and arbitrary document content
are not copied into the run. PDF routes are included in external export policies.

Registered host actions can be selected individually; plugin groups cover mutations.
Resource IDs and allowlisted input fields provide document, project, calendar,
billing, and other plugin context. Kanban adds column transitions, ticket creation/
deletion, and sprint start/completion. It compares saved board state for bulk saves
and move endpoints, preventing bulk edits from bypassing transition bindings.
Original board/column/transition permissions remain enforced. Reviewed plugins can
implement `WorkflowActionContextProvider` for context and logical selectors.
Declare `actions` and optionally `actionsFor(method, path)` for precise matching;
mark a descriptor `external=true` to apply the external category before execution.
The action catalogue and palette pick up these contracts without editing core UI.

## Modify export options

A Code step can return supported `export.*` values. The Code inspector lists the
connected module's output keys and offers an example. Put this step before review,
so the reviewer sees **Proposed export options**. Values apply only when the server
atomically consumes all completed approvals for that exact original request. Conflicting
outputs from multiple required workflows reject the attempt. Client headers and
parameters cannot supply an approved-options map.

| Action | Supported outputs |
| --- | --- |
| Generate project report | `export.from`, `export.to`, `export.report.kind` (`SPRINT`, `MONTHLY`, `PERIOD`), `export.report.title`, `export.report.includeDescriptions`, `export.report.includeAiSummary` |
| Export knowledge book | `export.knowledge.title` |
| Export approved timesheets | `export.from`, `export.to` |
| Analytics CSV/PDF export | `export.format` (`CSV`, `PDF`) |

```python
def run(context):
    return {"export.report.title": "Reviewed project report", "export.report.includeDescriptions": False, "export.report.includeAiSummary": False}
```

Dates use ISO `YYYY-MM-DD`; booleans use `True`/`False`; titles allow 1–200 printable
characters. The original service still validates date windows, board and project
access, document policies and recipients. Outputs cannot add recipients, change
resource ownership, or override access checks. Generation changes a newly generated
report; exporting an existing saved PDF applies its workflow policy and preserves
the stored document. This is an explicit adapter contract, not arbitrary request
rewriting or an executor for arbitrary plugin APIs.

## Outbound boundaries

Explicit email, notification transports, outgoing webhooks, and provider mutations
through the shared integration HTTP client check the external policy at dispatch.
Named email/download/export/send routes are recognized before action execution.
Authentication/OTP flows are exempt so approval cannot block sign-in.

Background sends without an interactive approval fail closed when the external
policy applies. This change does not add a durable outbound-resume queue or automatic
external retry. Packaged-worker callbacks require a reviewed adapter to carry approval
into a separate request. This is an application integration contract, not a network
firewall for arbitrary native code. AI calls, raw cloud-storage SDK operations, and
internal persistence are outside general outbound enforcement here; those broader
interception points require separate review because they affect core processing.

See [document/workflow usage](documents-and-workflows.md) and
[plugin notification delivery](plugins/notifications.md).
