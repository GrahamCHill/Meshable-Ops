"""Explicit registration for reviewed Python source plugins; image rebuilds install code."""
from fastapi import Request


def register_routes(app, payload, render_payload):
    from plugins.documents.routes import router
    app.include_router(router(payload, render_payload))
    @app.post("/plugins/billing/render")
    async def billing(request: Request):
        from fastapi import HTTPException
        from plugins.billing.renderer import BillingPdf
        value = await payload(request, "billing")
        try:
            BillingPdf.model_validate(value["billing"])
        except ValueError:
            raise HTTPException(400, "Invalid billing document") from None
        value["operation"] = "billing.render"
        return await render_payload(value)


def render_plugin(operation, value):
    from plugins.documents.renderer import render
    from plugins.billing.renderer import render as render_billing
    handlers = {"documents.render": lambda: render(value["template"], value.get("values", {})), "billing.render": lambda: render_billing(value["billing"])}
    if operation not in handlers:
        raise ValueError("Unknown plugin operation")
    return handlers[operation]()
