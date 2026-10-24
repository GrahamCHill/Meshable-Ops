import asyncio
import base64
import importlib.util
import json
import os
import socket
import struct
import tempfile
import unittest
from pathlib import Path
from unittest.mock import MagicMock, patch
import httpx
import app
from execution import container_options, execute, ExecutionFailed

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('bootstrap', ROOT / 'workers/entry.py')
bootstrap = importlib.util.module_from_spec(spec); spec.loader.exec_module(bootstrap)


class RuntimeTests(unittest.TestCase):
    def test_worker_policy_has_no_network_mounts_privileges_or_secrets(self):
        options = container_options('trusted-image')
        self.assertEqual(options['network_mode'], 'none')
        self.assertEqual(options['user'], '10001:10001')
        self.assertTrue(options['read_only'])
        self.assertEqual(options['cap_drop'], ['ALL'])
        self.assertEqual(options['mem_limit'], options['memswap_limit'])
        self.assertEqual(options['pids_limit'], 32)
        self.assertEqual(options['environment'], {})
        self.assertNotIn('volumes', options)
        self.assertIn('noexec', options['tmpfs']['/tmp'])

    def test_bootstrap_rejects_traversal_absolute_and_foreign_artifacts(self):
        for path in ('../evil.py', '/backend/evil.py', 'backend/../evil.py', 'backend//evil.py', 'frontend/evil.py'):
            with tempfile.TemporaryDirectory() as directory:
                with self.assertRaises(ValueError):
                    bootstrap.prepare({'runtime': 'PYTHON', 'entrypoint': path, 'files': {path: 'x'}, 'request': {}}, Path(directory) / 'job')

    def test_bootstrap_rejects_invalid_runtime_and_missing_entrypoint(self):
        for runtime, entry in [('SHELL', 'backend/a.py'), ('KOTLIN', 'backend/a.py'), ('PYTHON', 'backend/missing.py')]:
            with tempfile.TemporaryDirectory() as directory:
                with self.assertRaises(ValueError):
                    bootstrap.prepare({'runtime': runtime, 'entrypoint': entry, 'files': {'backend/a.py': 'x'}, 'request': {}}, Path(directory) / 'job')

    def test_kotlin_jar_is_decoded_and_request_has_no_callback_credentials(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory) / 'job'
            bootstrap.prepare({'runtime': 'KOTLIN', 'entrypoint': 'backend/app.jar', 'files': {'backend/app.jar': base64.b64encode(b'JAR').decode()}, 'request': {'body': {'safe': True}}}, root)
            self.assertEqual((root / 'backend/app.jar').read_bytes(), b'JAR')
            self.assertEqual(json.loads((root / 'request.json').read_text()), {'body': {'safe': True}})

    def test_fixed_image_and_bootstrap_never_expose_callback_token(self):
        client = MagicMock(); container = client.containers.create.return_value
        stream = container.attach_socket.return_value._sock
        result = json.dumps({'type': 'result', 'status': 201, 'result': {'ok': True}}).encode() + b'\n'
        stream.recv.return_value = b'\1\0\0\0' + struct.pack('>I', len(result)) + result
        value = {'runtime': 'PYTHON', 'entrypoint': 'backend/app.py', 'files': {}, 'request': {}, 'callbackToken': 'private-ticket', 'image': 'attacker-image'}
        self.assertEqual(execute(client, value, 'http://host/callback'), {'status': 201, 'body': {'ok': True}})
        args = client.containers.create.call_args.kwargs
        self.assertEqual(args['image'], 'meshable-ops-plugin-python:1')
        self.assertNotIn(b'private-ticket', stream.sendall.call_args.args[0])
        container.remove.assert_called_once_with(force=True)

    def test_worker_redirect_status_and_unbounded_protocol_are_rejected_and_removed(self):
        for message in ({'type': 'result', 'status': 302, 'result': {}}, {'type': 'call', 'id': 1}):
            client = MagicMock(); container = client.containers.create.return_value
            line = json.dumps(message).encode() + b'\n'
            container.attach_socket.return_value._sock.recv.return_value = b'\1\0\0\0' + struct.pack('>I', len(line)) + line
            with self.assertRaises(ExecutionFailed):
                execute(client, {'runtime': 'PYTHON', 'entrypoint': 'backend/app.py', 'files': {}, 'request': {}, 'callbackToken': 'x'}, 'http://host/callback')
            container.remove.assert_called_once_with(force=True)


class ControlPlaneTests(unittest.IsolatedAsyncioTestCase):
    async def request(self, token=None, body=b'{}'):
        async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app.app), base_url='http://runner') as client:
            return await client.post('/execute', content=body, headers={'Authorization': 'Bearer ' + token} if token else {})

    async def test_missing_configuration_and_unauthenticated_calls_cannot_reach_docker(self):
        with patch.dict(os.environ, {'APP_PLUGIN_RUNNER_TOKEN': ''}), patch.object(app, 'client') as engine:
            self.assertEqual((await self.request()).status_code, 503)
            engine.assert_not_called()
        with patch.dict(os.environ, {'APP_PLUGIN_RUNNER_TOKEN': 's' * 40}), patch.object(app, 'client') as engine:
            self.assertEqual((await self.request('wrong')).status_code, 401)
            engine.assert_not_called()

    async def test_authenticated_invalid_body_fails_before_starting_a_worker(self):
        with patch.dict(os.environ, {'APP_PLUGIN_RUNNER_TOKEN': 's' * 40}), patch.object(app, 'client') as engine:
            for body in (b'[]', b'not-json'):
                self.assertEqual((await self.request('s' * 40, body)).status_code, 400)
            engine.assert_not_called()

    async def test_successful_execution_closes_the_docker_client(self):
        engine = MagicMock()
        with patch.dict(os.environ, {'APP_PLUGIN_RUNNER_TOKEN': 's' * 40}), patch.object(app, 'client', return_value=engine), patch.object(app, 'execute', return_value={'status':200,'body':{'ok':True}}):
            result = await self.request('s' * 40)
            self.assertEqual(result.status_code, 200)
            self.assertEqual(result.json()['body'], {'ok':True})
        engine.close.assert_called_once()

    async def test_busy_runner_rejects_before_parsing_and_oversized_input_releases_capacity(self):
        with patch.dict(os.environ, {'APP_PLUGIN_RUNNER_TOKEN': 's' * 40}), patch.object(app, 'client') as engine:
            with patch.object(app, 'slots') as slots:
                slots.acquire.return_value = False
                self.assertEqual((await self.request('s' * 40, b'not-json')).status_code, 503)
                slots.release.assert_not_called()
            with patch.object(app, 'MAX_REQUEST', 16):
                self.assertEqual((await self.request('s' * 40, b'x' * 17)).status_code, 413)
            self.assertEqual((await self.request('s' * 40, b'not-json')).status_code, 400)
            engine.assert_not_called()


if __name__ == '__main__': unittest.main()
