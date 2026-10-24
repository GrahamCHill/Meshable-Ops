"""WeasyCanvas template-to-PDF contribution owned by the Documents plugin."""
from backend.generator import DocumentTemplate, DocumentDraft, render_document_html, paragraph_indices, section_id
from resources import inline_only

MAX_PDF_BYTES = 10 * 1024 * 1024


def make_draft(template, values):
    template = DocumentTemplate.model_validate(template)
    source = template.model_dump(exclude_none=True)
    defaults = dict(source["defaults"])
    # Match the browser factory: order content sections and honor their min/max paragraph rules.
    normalized = {key: [] for key in ("paragraphs", "paragraphSectionIds", "subparagraphs", "subsubparagraphs", "paragraphStyles", "subparagraphStyles", "subsubparagraphStyles")}
    for section in template.sections:
        if section.kind not in ("body", "text"):
            continue
        rules = section.rules or template.rules
        indices = paragraph_indices(template.defaults, template.sections, section)
        count = max(rules.minParagraphs, min(rules.maxParagraphs, len(indices)))
        for offset in range(count):
            index = indices[offset] if offset < len(indices) else -1
            def row(key, fallback):
                values = defaults.get(key) or []
                return values[index] if 0 <= index < len(values) else fallback
            normalized["paragraphs"].append(row("paragraphs", "New paragraph.") or "New paragraph.")
            normalized["paragraphSectionIds"].append(section_id(section))
            normalized["subparagraphs"].append(row("subparagraphs", []) if rules.allowSubparagraphs else [])
            normalized["subsubparagraphs"].append(row("subsubparagraphs", []) if rules.allowSubparagraphs and rules.allowSubsubparagraphs is not False else [])
            normalized["paragraphStyles"].append(row("paragraphStyles", {}))
            normalized["subparagraphStyles"].append(row("subparagraphStyles", []) if rules.allowSubparagraphs else [])
            normalized["subsubparagraphStyles"].append(row("subsubparagraphStyles", []) if rules.allowSubparagraphs and rules.allowSubsubparagraphs is not False else [])
    defaults.update(normalized)
    fields = {field.key for field in template.apiFields}
    if set(values) - fields:
        raise ValueError("Unknown document data fields")
    if any(image.assetKey for image in template.images):
        raise ValueError("Use embedded template images; external image keys are not supported by this host")
    return DocumentDraft.model_validate({**defaults, "template": source, "apiValues": values,
        "layout": source["layout"], "formatting": source["formatting"], "images": source.get("images", []),
        "signatures": source.get("signatures", [])})


def render(template, values):
    from weasyprint import HTML
    document = HTML(string=render_document_html(make_draft(template, values)), url_fetcher=inline_only).render()
    if not 1 <= len(document.pages) <= 200:
        raise ValueError("Use between 1 and 200 pages")
    result = document.write_pdf()
    if len(result) > MAX_PDF_BYTES:
        raise ValueError("Generated PDF exceeds 10 MB")
    return result


