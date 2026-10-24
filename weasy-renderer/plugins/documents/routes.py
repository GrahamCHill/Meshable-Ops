"""Internal Documents plugin API; Kotlin owns activation, identity, RBAC, and persistence."""
from fastapi import APIRouter, HTTPException, Request
from backend.generator import DocumentTemplate


def router(payload, render_payload):
    routes = APIRouter()

    @routes.post("/validate", include_in_schema=False)
    @routes.post("/plugins/documents/validate")
    async def validate(request: Request):
        value = await payload(request)
        try:
            template = DocumentTemplate.model_validate(value["template"])
            if any(image.assetKey for image in template.images):
                raise ValueError()
            return {"valid": True}
        except ValueError:
            raise HTTPException(400, "Invalid template structure, field bindings, image source, or layout") from None

    @routes.post("/render", include_in_schema=False)
    @routes.post("/plugins/documents/render")
    async def render(request: Request):
        value = await payload(request)
        return await render_payload({**value, "operation": "documents.render"})

    return routes
