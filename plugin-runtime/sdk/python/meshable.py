"""Meshable Ops plugin worker protocol v1. Code runs inside an isolated job, not the application process."""
import json
import os
import sys

MAX_MESSAGE = 4 * 1024 * 1024


class Response:
    def __init__(self, body, status=200):
        self.body, self.status = body, status


class Host:
    def __init__(self):
        self.sequence = 0

    def call(self, operation, **params):
        self.sequence += 1
        message = {'type': 'call', 'id': str(self.sequence), 'operation': operation, 'params': params}
        encoded = json.dumps(message, separators=(',', ':'))
        if len(encoded.encode()) > MAX_MESSAGE:
            raise ValueError('Host call exceeds limits')
        print(encoded, flush=True)
        raw = sys.stdin.buffer.readline(MAX_MESSAGE + 1)
        if len(raw) > MAX_MESSAGE:
            raise ValueError('Host response exceeds limits')
        result = json.loads(raw)
        if result.get('id') != str(self.sequence) or 'error' in result:
            raise RuntimeError(result.get('error', 'Invalid host response'))
        return result.get('result')

    def list(self, table, offset=0): return self.call('data.list', table=table, offset=offset)
    def get(self, table, record_id): return self.call('data.get', table=table, id=record_id)
    def create(self, table, data): return self.call('data.create', table=table, data=data)
    def update(self, table, record_id, data, revision): return self.call('data.update', table=table, id=record_id, data=data, revision=revision)
    def remove(self, table, record_id, revision): return self.call('data.remove', table=table, id=record_id, revision=revision)
    def http(self, method, url, **options): return self.call('http', method=method, url=url, **options)


def serve(handler):
    """handler(request_dict, host) returns any JSON-compatible result; use stderr for local diagnostics."""
    try:
        with open(os.environ['MESHABLE_REQUEST_FILE'], encoding='utf-8') as source:
            request = json.load(source)
        value = handler(request, Host())
        reply = value if isinstance(value, Response) else Response(value)
        result = json.dumps({'type': 'result', 'status': reply.status, 'result': reply.body}, separators=(',', ':'))
        if len(result.encode()) > MAX_MESSAGE:
            raise ValueError('Result exceeds limits')
        print(result, flush=True)
    except Exception:
        print('{"type":"error"}', flush=True)
        sys.exit(2)
