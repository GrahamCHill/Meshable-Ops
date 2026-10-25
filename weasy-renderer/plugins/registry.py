"""Explicit registration for reviewed Python source plugins; image rebuilds install code."""
from fastapi import Request


def register_routes(app, payload, render_payload):
    from plugins.documents.routes import router
    app.include_router(router(payload, render_payload))
    @app.post("/plugins/knowledge/render")
    async def knowledge(request: Request):
        from fastapi import HTTPException
        from plugins.knowledge.renderer import KnowledgeBook
        value = await payload(request, "knowledge")
        try:
            KnowledgeBook.model_validate(value["knowledge"])
        except ValueError:
            raise HTTPException(400, "Invalid knowledge book") from None
        value["operation"] = "knowledge.render"
        return await render_payload(value)
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

    @app.post("/plugins/analytics/render")
    async def analytics(request: Request):
        from fastapi import HTTPException
        from plugins.analytics.renderer import AnalyticsPdf
        value = await payload(request, "analytics")
        try:
            AnalyticsPdf.model_validate(value["analytics"])
        except ValueError:
            raise HTTPException(400, "Invalid analytics document") from None
        value["operation"] = "analytics.render"
        return await render_payload(value)


def render_plugin(operation, value):
    from plugins.documents.renderer import render
    from plugins.billing.renderer import render as render_billing
    from plugins.analytics.renderer import render as render_analytics
    from plugins.knowledge.renderer import render as render_knowledge
    handlers = {"documents.render": lambda: render(value["template"], value.get("values", {})), "billing.render": lambda: render_billing(value["billing"]), "analytics.render": lambda: render_analytics(value["analytics"]), "knowledge.render": lambda: render_knowledge(value["knowledge"])}
    if operation not in handlers:
        raise ValueError("Unknown plugin operation")
    return handlers[operation]()
