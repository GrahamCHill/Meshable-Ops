"""Explicit registration for reviewed Python source plugins; image rebuilds install code."""


def register_routes(app, payload, render_payload):
    from plugins.documents.routes import router
    app.include_router(router(payload, render_payload))


def render_plugin(operation, value):
    from plugins.documents.renderer import render
    handlers = {"documents.render": lambda: render(value["template"], value.get("values", {}))}
    if operation not in handlers:
        raise ValueError("Unknown plugin operation")
    return handlers[operation]()
