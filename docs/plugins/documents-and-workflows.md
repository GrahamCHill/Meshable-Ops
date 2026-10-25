# Optional document and workflow plugins

**Document generator** (`documents`) and **Workflows** (`workflows`) are bundled, first-class source plugins. Enable either under **Administration → Plugins → Manage**. They use the same lifecycle controls, role restrictions, table policies, dependency presentation, removal/restore, and explicit data clearing as other bundled plugins.

When enabled, Documents stays at `/documents` and its template editor at `/admin/document-templates`. Workflows stays at `/workflows` and its visual builder at `/admin/workflows`. Admin authoring appears as **Document templates** and **Workflow builder** cards in **Administration → Project Management**, alongside **Project settings** and **Report covers**. The section retains its `documents-workflows` ID for existing plugin assignments and bookmarks. The plugin management page also links directly to these screens. Disabling/removing a module hides these entries, removes its effective feature permissions and notification choices, and rejects direct APIs. Background workflow alerts also check activation and the owner's access before delivery.

## Independent components and composition

Workflows can run Start, Review, Condition, and End blocks independently. Documents supplies the optional `documents.pdf` capability for PDF attachments and Email blocks; Workflows advertises this relationship with `extends` and a conditional feature. The editor hides PDF creation/attachment controls when the document library is unavailable. Published designs needing PDFs display an unavailable reason when Documents or its table access is disabled. Existing PDF runs pause and retain their progress; they can be resumed after re-enablement or explicitly cancelled. Ordinary workflows remain usable.

Document generation remains useful without Workflows: templates, calculated fields, PDF previews/downloads, and explicit email attachments all work independently. Workflow links disappear from Documents when Workflows is disabled. A published run can grant its reached PDFs without granting the member access to the author's private document library. Plugin role/table restrictions and the core download/watermark policy still apply.

Core storage browsing, file sharing, PDF download policies, and project reporting remain available independently. The shared Python renderer continues serving project reports even with both plugins disabled. The internal worker has no public user API; Kotlin checks module activation, account permissions, ownership and policy before calling it.

## Upgrade and retained data

New databases start with both modules disabled. A one-time startup insert enables Documents if existing templates or generated PDFs are present, and Workflows if designs or saved runs are present. This preserves an existing installation's use of these features. An existing plugin settings row is never overwritten, including an explicit disabled or removed state.

The existing permission IDs (`documents`, `admin.templates`, `workflows`, `admin.workflows`), routes, notification IDs, UUIDs, revisions, and physical table names are retained. No records are copied, reseeded or renamed. Saved feature grants remain in access policies; activation controls the effective features returned to the frontend. Disable/removal does not delete data. Database backups and wrapping keys must survive application updates as described in [compatibility and updates](compatibility-and-updates.md).

The reviewed source definitions declare their pre-plugin tables: Documents owns `generated_documents` and `document_templates`; Workflows owns `workflow_runs` and `workflow_definitions`. Only compiled definitions can declare this retention exception; uploaded manifests still require their own `plugin_<id>_*` namespace. Each service applies table policy as well as record/feature access. **Clear data** requires typing the module ID and is destructive. Documents refuses clearing while workflow designs or run snapshots reference PDFs; remove those references or explicitly clear Workflows first. Disable/restore is the appropriate action when records should be retained.

## Full-stack source layout

| Contribution | Documents | Workflows |
| --- | --- | --- |
| Kotlin APIs, services, entities, permissions | `backend/src/main/kotlin/com/starter/plugins/documents/` | `backend/src/main/kotlin/com/starter/plugins/workflows/` |
| React pages, API contracts, components | `frontend/src/plugins/modules/documents/` | `frontend/src/plugins/modules/workflows/` |
| Python routes and rendering worker | `weasy-renderer/plugins/documents/` | Uses Documents only for optional PDF steps |

`backend/.../rendering/WeasyRendererClient.kt` is the shared private service transport. `weasy-renderer/app.py`, `worker.py`, and `resources.py` own authentication, bounded input, process/time limits and raster-resource restrictions. `plugins/registry.py` explicitly registers reviewed Python routes/operations. Documents registers `/plugins/documents/validate` and `/plugins/documents/render`; legacy internal `/validate` and `/render` aliases remain. Core `/reports/render` is independent. The vendored WeasyCanvas contract remains under `weasy-renderer/backend/` and `frontend/src/vendor/weasycanvas/`.

Read [full-stack plugin authoring](full-stack.md) for the supported Kotlin/React/Python deployment model, and the [feature guide](../documents-and-workflows.md) for editing, generation, email and workflow behavior.
