# WeasyCanvas source integration

Source snapshot copied from the local WeasyCanvas project, editor contract v0.8.0.
`editor/` contains the reusable React component/utilities; `src/` contains the reference
template designer and its model/helpers. Standalone routes, local persistence, account
state, demo shell, and image storage endpoints are not imported into this application.

Host integration lives in `components/documents/` and authenticated application APIs.
Host adaptations include React 18-compatible inert measurement markup, clearer data-field
labels, removal of decorative glyphs, and server-save/create-document callbacks.
Designer styles are scoped to `.weasy-host` and simplified to match this app. The matching
Python model/renderer sources are under `weasy-renderer/backend/`; its host wrapper blocks
external resource fetching and runs PDF rendering in bounded child processes.

To update, copy a matching editor/designer/Python source revision together, review host
adaptations, and run frontend, renderer, document access, and workflow validation tests.
The source project is not changed by this integration. Imported templates retain their
WeasyCanvas schema version; application revision numbers are stored separately.
