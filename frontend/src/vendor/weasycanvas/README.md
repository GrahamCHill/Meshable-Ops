# WeasyCanvas source integration

Source snapshot copied from the local WeasyCanvas project, editor contract v0.8.0.
`editor/` contains the reusable React component/utilities; `src/` contains the reference
template designer and its model/helpers. Standalone routes, local persistence, account
state, demo shell, and image storage endpoints are not imported into this application.

Host integration lives in `plugins/modules/documents/` and its authenticated Kotlin/Python plugin APIs. The optional Workflows module composes with generated PDFs.
Host adaptations include React 18-compatible inert measurement markup, clearer data-field
labels, removal of decorative glyphs, and server-save/create-document callbacks.
Designer styles are scoped to `.weasy-host` and simplified to match this app. The matching
Python model/renderer sources are under `weasy-renderer/backend/`; its host wrapper blocks
external resource fetching and runs PDF rendering in bounded child processes.

To update, copy a matching editor/designer/Python source revision together, review host
adaptations, and run frontend, renderer, document access, and workflow validation tests.
The source project is not changed by this integration. Imported templates retain their
WeasyCanvas schema version; application revision numbers are stored separately.

## Licensing of this snapshot

The project owner has included this WeasyCanvas source snapshot under the same AGPL-3.0-only / separately agreed commercial licensing choice as Meshable Ops. See the repository [licensing guide](../../../../LICENSING.md). Dependencies retain their upstream terms. This notice applies to the imported copy and does not change the separate source project.
