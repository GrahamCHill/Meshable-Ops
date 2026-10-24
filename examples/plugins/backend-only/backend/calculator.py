# SPDX-License-Identifier: AGPL-3.0-only
from meshable import serve, Response


def calculate(request, host):
    values = (request.get('body') or {}).get('values')
    if not isinstance(values, list) or len(values) > 100 or any(type(value) not in (int, float) for value in values):
        return Response({'error': 'Provide up to 100 numbers in values'}, 422)
    return {'total': sum(values), 'count': len(values)}


serve(calculate)
