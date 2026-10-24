"""Validated document/template contract and the independent WeasyPrint renderer."""
from html import escape
from pathlib import Path
import re
from string import Template
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator
from backend.media import DocumentImage, DocumentSignature, SignatureSettings, SIGNATURE_MARKER, media_margins, render_images, render_signatures, validate_media, normalize_signature_ids
from backend.api_fields import ApiField, validate_api_values, validate_api_bindings, hydrate_api_document
from backend.inline_fields import validate_inline_template

TEMPLATE_DIR = Path(__file__).resolve().parent / "templates"


def migrate_standalone_template(value):
    """Read older templates with a standalone signature section into the closing."""
    if not isinstance(value, dict) or not isinstance(value.get("sections"), list) or not isinstance(value.get("defaults"), dict):
        return value
    legacy = next((s for s in value["sections"] if isinstance(s, dict) and s.get("kind") == "signature"), None)
    closing = value["defaults"].get("closing_text")
    if legacy is None or not isinstance(closing, str):
        return value
    if not SIGNATURE_MARKER.search(closing):
        closing = closing.rstrip() + "\n\n{{signature}}"
    sections = [s for s in value["sections"] if not (isinstance(s, dict) and s.get("kind") == "signature")]
    if not any(isinstance(s, dict) and s.get("kind") == "closing" for s in sections):
        footer = next((i for i, s in enumerate(sections) if isinstance(s, dict) and s.get("kind") == "footer"), len(sections))
        sections.insert(footer, {"kind": "closing", "label": "Closing"})
    sections = grow_closing_limit(sections, len(closing))
    settings = value.get("signature") or {}
    if not isinstance(settings, dict):
        return value
    return {**value, "defaults": {**value["defaults"], "closing_text": closing}, "sections": sections,
            "signature": {**settings, "required": settings.get("required") is True or legacy.get("required") is not False}}


def grow_closing_limit(sections, length):
    return [{**s, "maxLength": max(s.get("maxLength", 4000), length)}
            if isinstance(s, dict) and s.get("kind") == "closing" and isinstance(s.get("maxLength", 4000), (int, float)) else s for s in sections]


def migrate_standalone_document(value):
    if not isinstance(value, dict) or not isinstance(value.get("template"), dict):
        return value
    original = value["template"]
    template = migrate_standalone_template(original)
    if template is original or not isinstance(value.get("closing_text", "End of document."), str):
        return value
    closing = value.get("closing_text", "End of document.")
    if not SIGNATURE_MARKER.search(closing):
        closing = closing.rstrip() + "\n\n{{signature}}"
    settings, signing = template["signature"], value.get("signature") or {}
    if not isinstance(signing, dict):
        return value
    return {**value, "closing_text": closing, "template": {**template, "sections": grow_closing_limit(template["sections"], len(closing))},
            "signature": {**settings, **signing, "required": settings.get("required") is True or signing.get("required") is True}}


def migrate_signature_template(value):
    value = migrate_standalone_template(value)
    if not isinstance(value, dict):
        return value
    result = {k: v for k, v in value.items() if k != "signature"}
    if "signature" in value:
        result.setdefault("signatures", [value["signature"]] if value["signature"] else [])
    if "signatures" in result:
        result["signatures"] = normalize_signature_ids(result["signatures"])
    return result


def migrate_signature_document(value):
    value = migrate_standalone_document(value)
    if not isinstance(value, dict):
        return value
    result = {k: v for k, v in value.items() if k != "signature"}
    if "signature" in value:
        result.setdefault("signatures", [value["signature"]] if value["signature"] else [])
    if "template" in result:
        result["template"] = migrate_signature_template(result["template"])
    if "signatures" in result:
        template = result.get("template")
        result["signatures"] = normalize_signature_ids(result["signatures"], template.get("signatures") if isinstance(template, dict) else None)
    return result


def document_signatures(document):
    if not SIGNATURE_MARKER.search(document.closing_text) or (document.template and not any(s.kind == "closing" for s in document.template.sections)):
        return []
    return document.signatures or ([DocumentSignature(**s.model_dump()) for s in document.template.signatures] if document.template and document.template.signatures else [DocumentSignature(id="signature-1")])


class Model(BaseModel):
    model_config = ConfigDict(extra="forbid")


class TextStyle(Model):
    fontFamily: Literal["Arial", "Georgia", "Times New Roman", "Verdana", "Courier New"] | None = None
    fontSize: float | None = Field(default=None, ge=8, le=36)
    bold: bool | None = None
    italic: bool | None = None
    underline: bool | None = None
    align: Literal["left", "center", "right", "justify"] | None = None
    color: str | None = Field(default=None, pattern=r"^#[\da-fA-F]{6}$")
    lineHeight: float | None = Field(default=None, ge=1, le=2.5)


class Formatting(Model):
    body: TextStyle = Field(default_factory=lambda: TextStyle(fontFamily="Arial", fontSize=10.65, lineHeight=1.34))
    title: TextStyle = Field(default_factory=lambda: TextStyle(fontSize=15, bold=True))
    closing: TextStyle = Field(default_factory=TextStyle)


class PageSettings(Model):
    size: Literal["A4", "Letter"] = "A4"
    orientation: Literal["portrait", "landscape"] = "portrait"
    marginTop: float = Field(default=30, ge=25, le=50)
    marginRight: float = Field(default=20, ge=15, le=50)
    marginBottom: float = Field(default=25, ge=25, le=50)
    marginLeft: float = Field(default=20, ge=15, le=50)
    paragraphGap: float = Field(default=18, ge=4, le=32)

    @model_validator(mode="after")
    def writing_area(self):
        width, height = (210, 297) if self.size == "A4" else (215.9, 279.4)
        if self.orientation == "landscape":
            width, height = height, width
        if width - self.marginLeft - self.marginRight < 80 or height - self.marginTop - self.marginBottom < 100:
            raise ValueError("Margins must leave at least 80 × 100 mm of writing space.")
        return self


SectionKind = Literal["header", "title", "details", "text", "body", "closing", "footer"]


class TemplateRules(Model):
    minParagraphs: int = Field(default=1, ge=1, le=100)
    maxParagraphs: int = Field(default=100, ge=1, le=100)
    allowSubparagraphs: bool = True
    allowSubsubparagraphs: bool = True

    @model_validator(mode="after")
    def ordered_limits(self):
        if self.minParagraphs > self.maxParagraphs:
            raise ValueError("Minimum paragraphs cannot exceed maximum paragraphs.")
        return self


class Section(Model):
    bindings: dict[Literal["text", "heading", "reference", "recipient"], str] = Field(default_factory=dict, max_length=4)
    kind: SectionKind
    id: str | None = Field(default=None, min_length=1, max_length=100, pattern=r"^[a-zA-Z0-9_-]+$")
    rules: TemplateRules | None = None
    style: TextStyle | None = None
    numbered: bool | None = None
    label: str = Field(min_length=1, max_length=80)
    required: bool = True
    locked: bool = False
    maxLength: int = Field(default=4000, ge=1, le=4000)

    @model_validator(mode="after")
    def header_limit(self):
        if self.kind not in ("body", "text") and (self.rules is not None or self.style is not None or self.numbered is not None):
            raise ValueError("Paragraph rules and styles must belong to a paragraph section.")
        if self.kind == "text" and (self.numbered is True or self.rules and (self.rules.allowSubparagraphs or self.rules.allowSubsubparagraphs)):
            raise ValueError("General text cannot have numbering or nested clauses; use a Terms section.")
        if self.kind == "header" and self.maxLength > 160:
            raise ValueError("Header text is limited to 160 characters.")
        return self


class DocumentText(Model):
    referenceLabel: str | None = Field(default=None, max_length=80)
    recipientLabel: str | None = Field(default=None, max_length=80)
    footer: str | None = Field(default=None, max_length=4000)
    sectionHeadings: dict[str, str] = Field(default_factory=dict, max_length=10)

    @field_validator("sectionHeadings")
    @classmethod
    def headings(cls, value):
        if any(not re.fullmatch(r"[a-zA-Z0-9_-]{1,100}", key) or len(text) > 80 for key, text in value.items()):
            raise ValueError("Use stable section IDs and headings of at most 80 characters.")
        return value

    def strings(self):
        return [self.referenceLabel or "", self.recipientLabel or "", self.footer or "", *self.sectionHeadings.values()]


class TemplateDefaults(Model):
    text: DocumentText | None = None
    organization: str = Field(max_length=160)
    title: str = Field(max_length=4000)
    reference: str = Field(max_length=100)
    recipient: str = Field(max_length=500)
    paragraphs: list[str] = Field(max_length=100)
    paragraphSectionIds: list[str] | None = Field(default=None, max_length=100)
    subparagraphs: list[list[str]] = Field(max_length=100)
    subsubparagraphs: list[list[list[str]]] = Field(default_factory=list, max_length=100)
    closing_text: str = Field(max_length=4000)
    paragraphStyles: list[TextStyle] = Field(default_factory=list, max_length=100)
    subparagraphStyles: list[list[TextStyle]] = Field(default_factory=list, max_length=100)
    subsubparagraphStyles: list[list[list[TextStyle]]] = Field(default_factory=list, max_length=100)
    numbered: bool = True

    @model_validator(mode="after")
    def bounded_text(self):
        if any(len(s) > 4000 for s in self.paragraphs) or any(len(row) > 26 for row in self.subparagraphs) or any(len(s) > 4000 for row in self.subparagraphs for s in row):
            raise ValueError("Template defaults exceed the text limits.")
        if any(len(row) > 26 for row in self.subparagraphStyles):
            raise ValueError("Template defaults exceed the subparagraph style limits.")
        validate_subsubclauses(self.paragraphs, self.subparagraphs, self.subsubparagraphs, self.subsubparagraphStyles)
        return self


class DocumentTemplate(Model):
    apiFields: list[ApiField] = Field(default_factory=list, max_length=50)
    version: Literal[1]
    id: str = Field(max_length=100)
    name: str = Field(min_length=1, max_length=100)
    description: str = Field(max_length=500)
    sections: list[Section] = Field(min_length=1, max_length=15)
    rules: TemplateRules
    layout: PageSettings
    formatting: Formatting
    defaults: TemplateDefaults
    images: list[DocumentImage] = Field(default_factory=list, max_length=8)
    signatures: list[SignatureSettings] = Field(default_factory=list, max_length=4)

    @model_validator(mode="before")
    @classmethod
    def migrate_signature_section(cls, value):
        return migrate_signature_template(value)

    @model_validator(mode="after")
    def unique_sections(self):
        kinds = [s.kind for s in self.sections]
        keys = [section_id(section) for section in self.sections]
        singleton = [kind for kind in kinds if kind not in ("body", "text")]
        if len(set(keys)) != len(keys) or len(set(singleton)) != len(singleton) or not any(kind in ("body", "text") for kind in kinds) or sum(kind in ("body", "text") for kind in kinds) > 10:
            raise ValueError("Use unique section IDs, up to 10 paragraph sections, and include the document body.")
        validate_section_membership(self.defaults, self.sections)
        validate_api_bindings(self)
        validate_inline_template(self)
        total = sum(max((section.rules or self.rules).minParagraphs, min((section.rules or self.rules).maxParagraphs, len(paragraph_indices(self.defaults, self.sections, section)))) for section in self.sections if section.kind in ("body", "text"))
        if total > 100:
            raise ValueError("Use at most 100 paragraphs across all sections.")
        for section in self.sections:
            if section.kind == "text":
                indices = paragraph_indices(self.defaults, self.sections, section)
                if any(i < len(self.defaults.subparagraphs) and self.defaults.subparagraphs[i] for i in indices) or any(i < len(self.defaults.subsubparagraphs) and any(self.defaults.subsubparagraphs[i]) for i in indices):
                    raise ValueError("General text cannot contain nested clauses; use a Terms section.")
        validate_media(self.layout, self.images, self.sections, self.defaults.organization, self.defaults.reference, self.defaults.text.footer if self.defaults.text else None)
        if self.defaults.text and self.defaults.text.footer is not None and any(s.kind == "footer" and len(self.defaults.text.footer) > s.maxLength for s in self.sections):
            raise ValueError("Footer text exceeds the template character limit.")
        if any(s.required for s in self.signatures) and ("closing" not in kinds or not SIGNATURE_MARKER.search(self.defaults.closing_text)):
            raise ValueError("Add signature lines to the closing before requiring signatures.")
        return self


class DocumentDraft(Model):
    apiValues: dict = Field(default_factory=dict, max_length=50)
    text: DocumentText | None = None
    organization: str = Field(max_length=160)
    title: str = Field(max_length=4000)
    reference: str = Field(max_length=100)
    recipient: str = Field(max_length=500)
    paragraphs: list[str] = Field(min_length=1, max_length=100)
    paragraphSectionIds: list[str] | None = Field(default=None, max_length=100)
    subparagraphs: list[list[str]] = Field(default_factory=list, max_length=100)
    subsubparagraphs: list[list[list[str]]] = Field(default_factory=list, max_length=100)
    closing_text: str = Field(default="End of document.", max_length=4000)
    template: DocumentTemplate | None = None
    layout: PageSettings = Field(default_factory=PageSettings)
    formatting: Formatting = Field(default_factory=Formatting)
    paragraphStyles: list[TextStyle] = Field(default_factory=list, max_length=100)
    subparagraphStyles: list[list[TextStyle]] = Field(default_factory=list, max_length=100)
    subsubparagraphStyles: list[list[list[TextStyle]]] = Field(default_factory=list, max_length=100)
    numbered: bool = True
    images: list[DocumentImage] = Field(default_factory=list, max_length=8)
    signatures: list[DocumentSignature] = Field(default_factory=list, max_length=4)

    @model_validator(mode="before")
    @classmethod
    def migrate_signature_section(cls, value):
        return hydrate_api_document(migrate_signature_document(value))

    @field_validator("apiValues")
    @classmethod
    def api_values(cls, values):
        return validate_api_values(values)

    @field_validator("subparagraphs", "subparagraphStyles")
    @classmethod
    def validate_children(cls, rows):
        if any(len(row) > 26 for row in rows):
            raise ValueError("Use at most 26 subparagraphs per paragraph.")
        return rows

    @model_validator(mode="after")
    def validate_document(self):
        validate_subsubclauses(self.paragraphs, self.subparagraphs, self.subsubparagraphs, self.subsubparagraphStyles)
        validate_media(self.layout, self.images, self.template.sections if self.template else None, self.organization, self.reference, self.text.footer if self.text else None)
        if self.text and self.text.footer is not None and self.template and any(s.kind == "footer" and len(self.text.footer) > s.maxLength for s in self.template.sections):
            raise ValueError("Footer text exceeds the template character limit.")
        signing = document_signatures(self)
        rules = self.template.signatures if self.template else []
        if rules and self.signatures and (len(rules) != len(self.signatures) or any(field.id != rule.id for field, rule in zip(self.signatures, rules))):
            raise ValueError("Signature field IDs and order must match the template's signing fields.")
        for i in range(max(len(self.signatures), len(signing), len(rules))):
            field = self.signatures[i] if i < len(self.signatures) else None
            rule = rules[i] if i < len(rules) else None
            if ((field and field.required) or (rule and rule.required)) and (i >= len(signing) or not signing[i].dataUrl):
                raise ValueError(f"Add the required signature {i + 1} before exporting.")
        if any(s.dataUrl for s in self.signatures) and not signing:
            raise ValueError("A signature image must be anchored to a signature line.")
        grandchildren = [s for row in self.subsubparagraphs for children in row for s in children]
        fields = {"organization": [self.organization], "title": [self.title], "details": [self.reference, self.recipient], "body": self.paragraphs + [s for row in self.subparagraphs for s in row] + grandchildren, "closing": [self.closing_text]}
        if self.template:
            validate_section_membership(self, self.template.sections)
            for section in self.template.sections:
                if section.kind in ("body", "text"):
                    indices = paragraph_indices(self, self.template.sections, section)
                    values = [self.paragraphs[i] for i in indices]
                    children = [text for i in indices if i < len(self.subparagraphs) for text in self.subparagraphs[i]]
                    leaves = [text for i in indices if i < len(self.subsubparagraphs) for row in self.subsubparagraphs[i] for text in row]
                    values += children + leaves
                    rules = section.rules or self.template.rules
                    if not rules.minParagraphs <= len(indices) <= rules.maxParagraphs:
                        raise ValueError(f"{section.label}: use {rules.minParagraphs}–{rules.maxParagraphs} paragraphs.")
                    if (section.kind == "text" or not rules.allowSubparagraphs) and children:
                        raise ValueError(f"{section.label}: this template does not allow subparagraphs.")
                    if (section.kind == "text" or not rules.allowSubparagraphs or not rules.allowSubsubparagraphs) and leaves:
                        raise ValueError(f"{section.label}: this template does not allow subsubclauses.")
                else:
                    values = fields.get("organization" if section.kind == "header" else section.kind, [])
                if section.required and any(not text.strip() for text in values):
                    raise ValueError(f"{section.label}: fill in the required text.")
                if any(len(text) > section.maxLength for text in values):
                    raise ValueError(f"{section.label}: use at most {section.maxLength} characters per field.")
        else:
            for name, values in fields.items():
                if any(not s.strip() for s in values):
                    raise ValueError(f"{'paragraphs.0' if name == 'body' else name}: text is required.")
        if self.paragraphSectionIds is not None and self.template is None:
            raise ValueError("Paragraph section IDs require a template.")
        if len(self.subparagraphs) > len(self.paragraphs):
            raise ValueError("Subparagraphs must belong to an existing paragraph.")
        all_text = [s for values in fields.values() for s in values] + (self.text.strings() if self.text else [])
        if any(len(s) > 4000 for s in all_text):
            raise ValueError("Use at most 4,000 characters per text field.")
        if sum(map(len, all_text)) > 60_000:
            raise ValueError("Use at most 60,000 characters per document.")
        return self


def section_id(section):
    return section.id or section.kind


def paragraph_indices(content, sections, section):
    content_sections = [s for s in sections if s.kind in ("body", "text")]
    primary = next((section_id(s) for s in content_sections if s.kind == "body"), section_id(content_sections[0]))
    ids = content.paragraphSectionIds or [primary] * len(content.paragraphs)
    return [i for i, value in enumerate(ids) if value == section_id(section)]


def validate_section_membership(content, sections):
    ids = content.paragraphSectionIds
    allowed = {section_id(s) for s in sections if s.kind in ("body", "text")}
    if ids is not None and (len(ids) != len(content.paragraphs) or any(value not in allowed for value in ids)):
        raise ValueError("Each paragraph must belong to an existing paragraph section; supply one section ID per paragraph.")


def validate_subsubclauses(paragraphs, subparagraphs, subsubparagraphs, styles):
    if len(subsubparagraphs) > len(paragraphs) or any(len(row) > len(subparagraphs[i] if i < len(subparagraphs) else []) for i, row in enumerate(subsubparagraphs)):
        raise ValueError("Subsubclauses must belong to an existing subparagraph.")
    if any(len(children) > 26 for row in subsubparagraphs for children in row):
        raise ValueError("Use at most 26 subsubclauses per subparagraph.")
    if any(len(text) > 4000 for row in subsubparagraphs for children in row for text in children):
        raise ValueError("Use at most 4,000 characters per subsubclause.")
    if any(len(row) > 26 or any(len(children) > 26 for children in row) for row in styles):
        raise ValueError("Subsubclause styles exceed the nesting limits.")


def style_css(*styles: TextStyle) -> str:
    """Only validated enum, numeric and colour values can enter the CSS."""
    values = {}
    for style in styles:
        values.update(style.model_dump(exclude_none=True))
    css = []
    mapping = {"fontFamily": "font-family", "fontSize": "font-size", "align": "text-align", "lineHeight": "line-height", "color": "color"}
    for key, name in mapping.items():
        if key in values:
            value = f'"{values[key]}"' if key == "fontFamily" else f'{values[key]:g}pt' if key == "fontSize" else str(values[key])
            css.append(f"{name}:{value}")
    for key, name, yes, no in [("bold", "font-weight", "700", "400"), ("italic", "font-style", "italic", "normal"), ("underline", "text-decoration", "underline", "none")]:
        if key in values:
            css.append(f"{name}:{yes if values[key] else no}")
    return ";".join(css)


def render_document_html(draft: DocumentDraft, asset_store=None) -> str:
    from backend.asset_storage import resolve_image_assets
    draft = draft.model_copy(update={"images": resolve_image_assets(draft.images, asset_store)})
    def text(value: str) -> str:
        return escape(value.replace("\r\n", "\n").replace("\r", "\n")).replace("\n", "<br>")

    def styled(content: str, *styles: TextStyle, tag="div", class_name="") -> str:
        return f'<{tag} class="{class_name}" style="{escape(style_css(*styles), quote=True)}">{content}</{tag}>'

    def render_body(section):
        items = []
        indices = paragraph_indices(draft, draft.template.sections, section) if draft.template else list(range(len(draft.paragraphs)))
        section_style = section.style if section and section.style else TextStyle()
        for index in indices:
            paragraph = draft.paragraphs[index]
            if section and section.kind == "text":
                parent_style = draft.paragraphStyles[index] if index < len(draft.paragraphStyles) else TextStyle()
                items.append(styled(text(paragraph), body_style, section_style, parent_style, tag="p", class_name="general-paragraph"))
                continue
            children = draft.subparagraphs[index] if index < len(draft.subparagraphs) else []
            parent_style = draft.paragraphStyles[index] if index < len(draft.paragraphStyles) else TextStyle()
            child_styles = draft.subparagraphStyles[index] if index < len(draft.subparagraphStyles) else []
            child_items = []
            for sub_index, child in enumerate(children):
                grandchildren = draft.subsubparagraphs[index][sub_index] if index < len(draft.subsubparagraphs) and sub_index < len(draft.subsubparagraphs[index]) else []
                styles = draft.subsubparagraphStyles[index][sub_index] if index < len(draft.subsubparagraphStyles) and sub_index < len(draft.subsubparagraphStyles[index]) else []
                leaves = '<ol type="i" class="subsubclauses">' + "".join(f'<li>{styled(text(leaf), body_style, section_style, styles[k] if k < len(styles) else TextStyle(), class_name="paragraph-text")}</li>' for k, leaf in enumerate(grandchildren)) + "</ol>" if grandchildren else ""
                child_items.append(f'<li>{styled(text(child), body_style, section_style, child_styles[sub_index] if sub_index < len(child_styles) else TextStyle(), class_name="paragraph-text")}{leaves}</li>')
            nested = '<ol type="a">' + "".join(child_items) + "</ol>" if children else ""
            items.append(f"<li>{styled(text(paragraph), body_style, section_style, parent_style, class_name='paragraph-text')}{nested}</li>")
        numbered = section.numbered if section and section.numbered is not None else draft.numbered
        list_style = ' style="list-style-type:disc"' if not numbered else ""
        primary = next((section_id(s) for s in draft.template.sections if s.kind == section.kind), section.kind) if draft.template else "body"
        key = section_id(section) if section else "body"
        heading_text = draft.text.sectionHeadings.get(section_id(section), section.label) if draft.text and section else section.label if section else ""
        heading = f'<h2 class="paragraph-section-heading">{text(heading_text)}</h2>' if draft.template and sum(s.kind in ("body", "text") for s in draft.template.sections) > 1 else ""
        images = render_images(draft.images, section.kind if section else "body", key, primary)
        if section and section.kind == "text":
            return heading + images + f'<div class="general-text">{"".join(items)}</div>'
        return heading + images + f'<ol class="body-list"{list_style}>{"".join(items)}</ol>'
    body_style = draft.formatting.body
    organization, reference = text(draft.organization), text(draft.reference)
    footer = text(draft.text.footer) if draft.text and draft.text.footer is not None else f"{organization} · {reference}"
    reference_label = text(draft.text.referenceLabel) if draft.text and draft.text.referenceLabel is not None else "Reference:"
    recipient_label = text(draft.text.recipientLabel) if draft.text and draft.text.recipientLabel is not None else "Prepared for:"
    signature_html = render_signatures(document_signatures(draft))
    marker = SIGNATURE_MARKER.search(draft.closing_text)
    closing = f'<div class="closing-text">{text(draft.closing_text[:marker.start()].rstrip()) or "&nbsp;"}</div>' + signature_html + f'<div class="closing-text">{text(draft.closing_text[marker.end():].lstrip()) or "&nbsp;"}</div>' if marker else text(draft.closing_text)
    sections = {
        "header": f'<header>{render_images(draft.images, "header")}{organization}</header>',
        "footer": f'<footer>{render_images(draft.images, "footer")}{footer}</footer>',
        "title": render_images(draft.images, "title") + styled(text(draft.title), body_style, draft.formatting.title, tag="h1"),
        "details": render_images(draft.images, "details") + styled(f'<div class="detail-row"><strong>{reference_label}</strong><div>{reference}</div></div><div class="detail-row"><strong>{recipient_label}</strong><div>{text(draft.recipient)}</div></div>', body_style, class_name="details"),
        "closing": styled(render_images(draft.images, "closing") + closing, body_style, draft.formatting.closing, class_name="closing"),
    }
    order = draft.template.sections if draft.template else [Section(kind=kind, label=kind, maxLength=160 if kind == "header" else 4000) for kind in ["header", "title", "details", "body", "closing", "footer"]]
    running = "".join(sections[kind] for kind in ["header", "footer"] if any(s.kind == kind for s in order))
    flow = "".join(render_body(section) if section.kind in ("body", "text") else sections[section.kind] for section in order if section.kind not in ["header", "footer"])
    layout = draft.layout
    margin_top, margin_bottom = media_margins(layout, draft.images, draft.organization, draft.reference, draft.template.sections if draft.template else None, draft.text.footer if draft.text else None)
    template = Template((TEMPLATE_DIR / "document.html").read_text(encoding="utf-8"))
    return template.substitute(title=text(draft.title), running=running, content=flow,
        page_size=f"{layout.size} {layout.orientation}", margin_top=margin_top, margin_right=layout.marginRight,
        margin_bottom=margin_bottom, margin_left=layout.marginLeft, paragraph_gap=layout.paragraphGap)


def render_document_pdf(draft: DocumentDraft, asset_store=None) -> bytes:
    from weasyprint import HTML

    return HTML(string=render_document_html(draft, asset_store), base_url=str(TEMPLATE_DIR)).write_pdf()
