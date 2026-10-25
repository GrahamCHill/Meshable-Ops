"""Docker control plane. Only fixed trusted worker images receive uploaded artifacts."""
import json
import logging
import os
import socket
import struct
import time
from threading import Timer
from datetime import datetime, timezone
import httpx

MAX_MESSAGE = 4 * 1024 * 1024
LABEL = 'com.meshable-ops.plugin-job'


class ExecutionFailed(Exception): pass


def container_options(image):
    from docker.types import LogConfig
    return dict(image=image, stdin_open=True, tty=False, detach=True, network_mode='none', read_only=True,
                user='10001:10001', cap_drop=['ALL'], security_opt=['no-new-privileges:true'],
                mem_limit='384m', memswap_limit='384m', nano_cpus=500000000, pids_limit=32,
                tmpfs={'/tmp': 'rw,noexec,nosuid,nodev,size=64m,mode=0700,uid=10001,gid=10001'},
                labels={LABEL: '1'}, environment={}, use_config_proxy=False,
                log_config=LogConfig(type='none'))


def reap(client):
    for container in client.containers.list(all=True, filters={'label': LABEL + '=1'}):
        try:
            created = datetime.fromisoformat(container.attrs['Created'].replace('Z', '+00:00'))
            if (datetime.now(timezone.utc) - created).total_seconds() > 90:
                container.remove(force=True)
        except Exception:
            pass


def execute(client, value, callback_url):
    images = {'PYTHON': os.getenv('PLUGIN_PYTHON_IMAGE', 'meshable-ops-plugin-python:1'),
              'KOTLIN': os.getenv('PLUGIN_KOTLIN_IMAGE', 'meshable-ops-plugin-kotlin:1')}
    if value.get('runtime') not in images or not isinstance(value.get('callbackToken'), str):
        raise ExecutionFailed()
    # No caller-supplied image, command, mounts, ports, network or Docker settings.
    container = connection = watchdog = None
    stage = 'image'
    deadline = time.monotonic() + 40
    try:
        client.images.get(images[value['runtime']])  # Never pull an image in response to an uploaded package.
        stage = 'create'
        container = client.containers.create(**container_options(images[value['runtime']]))
        def expire():
            try: container.kill()
            except Exception: pass
        watchdog = Timer(40, expire)
        watchdog.daemon = True
        watchdog.start()
        stage = 'attach'
        connection = container.attach_socket(params={'stdin': 1, 'stdout': 1, 'stderr': 1, 'stream': 1})
        stream = connection._sock
        stream.settimeout(2)
        container.start()
        stage = 'bootstrap'
        bootstrap = {key: value[key] for key in ('runtime', 'entrypoint', 'files', 'request')}
        stream.sendall(json.dumps(bootstrap, separators=(',', ':')).encode() + b'\n')
        stage = 'protocol'
        wire = bytearray(); stdout = bytearray(); stderr_bytes = 0; calls = 0
        with httpx.Client(follow_redirects=False, timeout=22, trust_env=False) as host:
            while time.monotonic() < deadline:
                try: chunk = stream.recv(65536)
                except socket.timeout: continue
                if not chunk: raise ExecutionFailed()
                wire.extend(chunk)
                while len(wire) >= 8:
                    channel, size = wire[0], struct.unpack('>I', wire[4:8])[0]
                    if size > MAX_MESSAGE: raise ExecutionFailed()
                    if len(wire) < size + 8: break
                    data = bytes(wire[8:8 + size]); del wire[:8 + size]
                    if channel == 2:
                        stderr_bytes += len(data)
                        if stderr_bytes > 65536: raise ExecutionFailed()
                        continue
                    if channel != 1: raise ExecutionFailed()
                    stdout.extend(data)
                    if len(stdout) > MAX_MESSAGE: raise ExecutionFailed()
                    while b'\n' in stdout:
                        line, _, rest = stdout.partition(b'\n'); stdout = bytearray(rest)
                        message = json.loads(line)
                        if not isinstance(message, dict): raise ExecutionFailed()
                        if message.get('type') == 'result':
                            result = message.get('result')
                            if len(json.dumps(result).encode()) > MAX_MESSAGE: raise ExecutionFailed()
                            status = message.get('status', 200)
                            if type(status) is not int or not (200 <= status <= 299 or 400 <= status <= 599): raise ExecutionFailed()
                            return {'status': status, 'body': result}
                        if message.get('type') != 'call' or calls >= 64 or not isinstance(message.get('id'), str): raise ExecutionFailed()
                        calls += 1
                        response = host.post(callback_url, headers={'X-Plugin-Invocation': value['callbackToken']},
                                             json={'operation': message.get('operation'), 'params': message.get('params')})
                        if len(response.content) > MAX_MESSAGE: raise ExecutionFailed()
                        reply = {'id': message['id'], 'result': response.json().get('result')} if response.status_code == 200 else {'id': message['id'], 'error': 'Host operation denied or unavailable'}
                        stream.sendall(json.dumps(reply, separators=(',', ':')).encode() + b'\n')
        raise ExecutionFailed()
    except Exception as error:
        # Fixed stage and exception type only: never source, request data, native messages or stderr.
        logging.getLogger(__name__).warning('Plugin job failed at %s (%s)', stage, type(error).__name__)
        raise ExecutionFailed() from None
    finally:
        if watchdog is not None: watchdog.cancel()
        if connection is not None: connection.close()
        if container is not None:
            try: container.remove(force=True)
            except Exception: pass
