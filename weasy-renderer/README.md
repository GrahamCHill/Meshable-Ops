# Internal WeasyCanvas renderer

This contains the matching WeasyCanvas v0.8.0 Python schema/generator snapshot under `backend/` and the application's restricted rendering wrapper. Browser editor/designer sources live under `frontend/src/vendor/weasycanvas/`. The original project is not a runtime dependency.

Run with the main Docker Compose stack. For native development, use Python 3.10+ in a virtual environment, install `requirements.txt` and WeasyPrint's Pango/native dependencies, then run:

```powershell
python -m uvicorn app:app --host 127.0.0.1 --port 8720
```

Set `APP_DOCUMENT_RENDER_URL=http://127.0.0.1:8720` on the Kotlin backend and the same optional `APP_DOCUMENT_RENDER_TOKEN` on both processes. Keep this service private. A configured token is required for `/validate` and `/render`. `/health` is public within its private network. Tokens are server configuration, not template data or browser fields.

Install `requirements-dev.txt`, then run `python -m unittest discover -s tests -v` from this directory. These tests include real PDF rendering and therefore require the native libraries. On Windows, set `WEASYPRINT_DLL_DIRECTORIES` and the process PATH to your Pango DLL directory when it is not auto-detected. The test fixture uses the same quote starter schema as the frontend. A sample PDF is written under ignored `target/` for visual QA. If using a sandbox, FastAPI's local test client/subprocesses may require socket/process permission.

`app.py` bounds input and runs one `worker.py` subprocess per PDF. The worker creates a document from saved template defaults/API values, normalizes paragraph sections, uses a raster-only URL fetcher, and limits pages/output. Linux also enforces per-worker CPU/address-space limits; the Compose service supplies container memory/process limits. Raw native errors and document values are not returned or logged by the wrapper. The vendored generator's unrestricted `render_document_pdf()` is not exposed by this host.

The document generator's Python routes and worker live in `plugins/documents/`; `plugins/registry.py` registers reviewed contributions explicitly. Kotlin/React plugin code lives in the corresponding backend/frontend module directories. The host retains token authentication, input/process/output limits and raster-only resource policy; core `/reports/render` remains available when the Document generator plugin is disabled. The worker is private and Kotlin enforces activation/RBAC before requests. See [full-stack plugins](../docs/plugins/full-stack.md) and [the application guide](../docs/documents-and-workflows.md) for deployment, policy, and persistence boundaries.

## Licensing

The original host wrapper and the project owner's imported WeasyCanvas model/generator snapshot are covered by the repository [AGPL/commercial licensing choice](../LICENSING.md). WeasyPrint, fonts, native libraries, and other dependencies retain their upstream licenses; see [third-party notices](../THIRD_PARTY_NOTICES.md).
