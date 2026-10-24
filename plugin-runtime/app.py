"""Private authenticated control plane. Uploaded code never executes in this service."""
import asyncio
import hmac
import json
import os
from threading import BoundedSemaphore
from contextlib import asynccontextmanager
import docker
from fastapi import FastAPI, HTTPException, Request
from starlette.concurrency import run_in_threadpool
from execution import execute, reap, ExecutionFailed

slots = BoundedSemaphore(4)


def client(): return docker.from_env(timeout=5)


@asynccontextmanager
async def lifespan(app):
    async def cleanup():
        while True:
            try:
                def sweep():
                    with client() as engine: reap(engine)
                await run_in_threadpool(sweep)
            except Exception: pass
            await asyncio.sleep(30)
    task = asyncio.create_task(cleanup())
    yield
    task.cancel()


app = FastAPI(docs_url=None, redoc_url=None, openapi_url=None, lifespan=lifespan)


@app.get('/health')
def health(): return {'status': 'UP', 'configured': len(os.getenv('APP_PLUGIN_RUNNER_TOKEN', '')) >= 32}


@app.post('/execute')
async def invoke(request: Request):
    token = os.getenv('APP_PLUGIN_RUNNER_TOKEN', '')
    if len(token) < 32: raise HTTPException(503, 'Plugin runner is not configured')
    if not hmac.compare_digest(request.headers.get('Authorization', '').encode(), ('Bearer ' + token).encode()):
        raise HTTPException(401, 'Runner authentication required')
    raw = bytearray()
    async for chunk in request.stream():
        raw.extend(chunk)
        if len(raw) > 48 * 1024 * 1024: raise HTTPException(413, 'Plugin execution request exceeds limits')
    try:
        value = json.loads(raw)
        if not isinstance(value, dict): raise ValueError()
    except ValueError: raise HTTPException(400, 'Invalid plugin request') from None
    if not slots.acquire(blocking=False): raise HTTPException(503, 'Plugin workers are busy')
    try:
        def run():
            with client() as engine:
                return execute(engine, value, os.getenv('PLUGIN_HOST_CALLBACK', 'http://backend:8080/internal/plugins/host-call'))
        return await run_in_threadpool(run)
    except Exception: raise HTTPException(503, 'Plugin execution failed or exceeded its limits') from None
    finally: slots.release()
