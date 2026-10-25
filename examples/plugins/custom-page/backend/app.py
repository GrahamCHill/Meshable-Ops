# SPDX-License-Identifier: LicenseRef-Meshable-Ops-Community-1.1
from meshable import serve, Response


def greet(request, host):
    name = (request.get('body') or {}).get('name')
    if not isinstance(name, str) or not 1 <= len(name.strip()) <= 100:
        return Response({'error': 'Enter a name of 1–100 characters.'}, 422)
    return {'message': f'Hello, {name.strip()}.', 'processedBy': 'Uploaded Python backend',
            'memberId': request['actor']['id']}


serve(greet)
