# Custom frontend + backend plugin template

This starter adds a custom page, a Guide submenu, a public setting, and Python server logic from **one uploaded ZIP**. It needs no app source registration, frontend build, npm/pip install, or app restart. It deliberately has no database/network grants. The Python SDK is supplied by the host worker.

1. Copy this directory and choose a unique `manifest.id` and name in `plugin.json`.
2. Fill in `author.json`; all four fields are mandatory but may be empty.
3. Edit `frontend/index.html`, `frontend/app.js`, and `backend/app.py`.
4. From this directory run `python package.py` in a repository checkout. Upload `target/custom-page-plugin.zip` in Administration → Plugins, review it, and enable it. The private backend runner must be configured.
5. Open **Custom page**, enter a name, and choose **Call backend**. The page calls `/api/plugins/custom-page/backend/greet`; the handler validates input and returns JSON. The Guide submenu demonstrates a second page.

The packager includes the corresponding source and root license. If copying the template outside the checkout, copy the root `LICENSE` too and adjust its path in `package.py`.

The host provides `meshableOps.context()`, `meshableOps.navigate(section)`, and `meshableOps.backend.request(path, body, method, query)`. Server handlers receive scoped actor metadata and public configuration. Use text rendering for data, never inject it as HTML. Add only the host permissions your code needs; do not put credentials in browser assets or the manifest. [Backend SDK and permissions](../../../docs/plugins/backend-runtime.md).

For **React plus Python and Kotlin/JVM** together, use the [full stack example](../full-stack/). Its build creates a self-contained React bundle and executable Kotlin JAR. A backend-only package can omit `PAGE`, the frontend files, and submenu definitions; see [backend-only example](../backend-only/).

Community plugins may be sold or offered through paid services while honoring their applicable license obligations. See [plugin licensing](../../../docs/plugins/licensing.md) and the [CLA's paid-plugin terms](../../../CLA.md#paid-community-plugins).
