# SPDX-License-Identifier: LicenseRef-Meshable-Ops-Community-1.0
from meshable import serve, Response

TABLE = 'plugin_zip_full_stack_notes'


def handle(request, host):
    if request['method'] == 'GET':
        return host.list(TABLE)
    title = (request.get('body') or {}).get('title', '')
    if not isinstance(title, str) or not title.strip() or len(title) > 100:
        return Response({'error': 'Provide a title of 1–100 characters'}, 422)
    return Response(host.create(TABLE, {'title': title.strip()}), 201)


serve(handle)
