# Backend-only ZIP example

From this directory:

```bash
python -m zipfile -c calculator.zip plugin.json author.json backend
```

Upload and enable it in Administration → Plugins. It has no page, menu or React dependency. An authenticated, permitted member can call:

```http
POST /api/plugins/zip-calculator/backend/calculate
Content-Type: application/json

{"values": [10, 20, 30]}
```

The uploaded Python code returns `{"total":60,"count":3}` or a `422` validation response. It has no database or HTTP grants. [Backend runtime and setup](../../../docs/plugins/backend-runtime.md). Original example code inherits the application's [AGPL community licensing](../../../LICENSING.md).
