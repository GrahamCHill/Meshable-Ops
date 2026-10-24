"""Run one reviewed PDF operation in a bounded child process."""
import json
import sys


if __name__ == "__main__":
    try:
        if sys.platform != "win32":
            import resource
            resource.setrlimit(resource.RLIMIT_AS, (768 * 1024 * 1024, 768 * 1024 * 1024))
            resource.setrlimit(resource.RLIMIT_CPU, (35, 35))
        request = json.loads(sys.stdin.buffer.read(2 * 1024 * 1024 + 1))
        if "report" in request:
            from report import render_report
            sys.stdout.buffer.write(render_report(request["report"]))
        else:
            from plugins.registry import render_plugin
            sys.stdout.buffer.write(render_plugin(request.get("operation", "documents.render"), request))
    except Exception:
        # Input/template values and native errors never reach the HTTP response or logs.
        sys.exit(2)
