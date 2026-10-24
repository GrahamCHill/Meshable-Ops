"""Internal WeasyCanvas rendering service. Host APIs own authentication and persistence."""
import hmac
import json
import os
from pathlib import Path
import subprocess
import sys
from threading import BoundedSemaphore

from fastapi import FastAPI, HTTPException, Request, Response

app = FastAPI(title="Internal WeasyCanvas renderer", docs_url=None, redoc_url=None, openapi_url=None)
workers = BoundedSemaphore(2)
MAX_BODY = 2 * 1024 * 1024


async def payload(request, kind="template"):
    token = os.getenv("APP_DOCUMENT_RENDER_TOKEN", "")
    if token and not hmac.compare_digest(request.headers.get("Authorization", "").encode(), ("Bearer " + token).encode()):
        raise HTTPException(401, "Renderer authentication required")
    raw = bytearray()
    async for chunk in request.stream():
        raw.extend(chunk)
        if len(raw) > MAX_BODY:
            raise HTTPException(413, "Template payload must be 2 MB or smaller")
    try:
        value = json.loads(raw)
        if not isinstance(value, dict) or not isinstance(value.get(kind), dict):
            raise ValueError()
        return value
    except (ValueError, UnicodeError):
        raise HTTPException(400, "Supply a valid template payload") from None


@app.get("/health")
def health():
    return {"status": "UP", "renderer": "WeasyCanvas 0.8.0 / WeasyPrint"}


@app.post("/reports/render")
async def render_report(request: Request):
    value = await payload(request, "report")
    from report import ReportDocument
    try:
        ReportDocument.model_validate(value["report"])
    except ValueError:
        raise HTTPException(400, "Invalid structured report") from None
    return await render_payload(value)


async def render_payload(value):
    if not workers.acquire(blocking=False):
        raise HTTPException(503, "Document rendering is busy")
    try:
        # Runs outside the web process with a hard wall-clock timeout; no persistent worker state.
        from starlette.concurrency import run_in_threadpool
        result = await run_in_threadpool(subprocess.run, [sys.executable, "-m", "worker"], input=json.dumps(value).encode(),
            capture_output=True, timeout=45, cwd=Path(__file__).parent)
        if result.returncode or not result.stdout.startswith(b"%PDF-") or len(result.stdout) > 10 * 1024 * 1024:
            raise HTTPException(400, "Document cannot be rendered. Check template rules and required fields.")
        return Response(result.stdout, media_type="application/pdf", headers={"Cache-Control": "no-store"})
    except subprocess.TimeoutExpired:
        raise HTTPException(503, "Document rendering timed out") from None
    finally:
        workers.release()


# Reviewed source plugins register routes explicitly; requests cannot import arbitrary modules.
from plugins.registry import register_routes
register_routes(app, payload, render_payload)
