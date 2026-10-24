"""API-owned text bindings. Values are supplied by the integrating host, never fetched here."""
import math
import re
from decimal import Decimal

from pydantic import BaseModel, ConfigDict, Field, field_validator
from backend.media import SIGNATURE_MARKER
from backend.inline_fields import hydrate_inline_document, resolve_implicit_footer


def valid_api_key(key):
    return isinstance(key, str) and re.fullmatch(r"[a-zA-Z][a-zA-Z0-9_.-]{0,119}", key) and not any(part in ("__proto__", "prototype", "constructor") for part in key.split("."))


class ApiField(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)
    key: str
    label: str = Field(min_length=1, max_length=80)
    required: bool = False
    maxLength: int = Field(default=4000, ge=1, le=4000)
    fallback: str = Field(default="", max_length=4000)

    @field_validator("key")
    @classmethod
    def key_format(cls, key):
        if not valid_api_key(key):
            raise ValueError("Use an API key starting with a letter and containing letters, numbers, underscores, dots or hyphens.")
        return key


def validate_api_values(values):
    if not isinstance(values, dict) or len(values) > 50:
        raise ValueError("Supply up to 50 API values in a JSON object.")
    total = 0
    for key, value in values.items():
        if not valid_api_key(key) or not (value is None or isinstance(value, (str, bool)) or isinstance(value, (int, float)) and math.isfinite(value) and abs(value) <= 9007199254740991):
            raise ValueError("API values must be text, finite numbers, booleans or null with valid keys.")
        if isinstance(value, str) and len(value) > 4000:
            raise ValueError("Use at most 4,000 characters per API value.")
        total += len(api_string(value)) if value is not None else 0
    if total > 60000:
        raise ValueError("Use at most 60,000 characters of API data.")
    return values


def api_string(value):
    if isinstance(value, bool):
        return "true" if value else "false"
    if isinstance(value, float) and value.is_integer():
        return str(int(value))
    if isinstance(value, float):
        return format(Decimal(repr(value)), "f") if abs(value) >= 0.000001 else re.sub(r"e-0+(\d)", r"e-\1", repr(value))
    return str(value)


def resolve_api_field(field, values, strict=True):
    raw = values.get(field.key)
    missing = raw is None or isinstance(raw, str) and not raw.strip()
    value = field.fallback if raw is None else api_string(raw)
    if strict and field.required and missing:
        raise ValueError(f"{field.label}: API value '{field.key}' is required.")
    if len(value) > field.maxLength:
        raise ValueError(f"{field.label}: API value '{field.key}' exceeds {field.maxLength} characters.")
    return value


def validate_api_bindings(template):
    fields = {f.key: f for f in template.apiFields}
    if len(fields) != len(template.apiFields) or any(not f.label.strip() or len(f.fallback) > f.maxLength for f in template.apiFields):
        raise ValueError("Use unique API keys, nonblank labels and fallbacks within their character limits.")
    primary = next((s.id or s.kind for s in template.sections if s.kind == "body"), next(s.id or s.kind for s in template.sections if s.kind in ("text", "body")))
    ids = template.defaults.paragraphSectionIds or [primary] * len(template.defaults.paragraphs)
    for section in template.sections:
        slots = ("reference", "recipient") if section.kind == "details" else ("text", "heading") if section.kind in ("text", "body") else ("text",)
        for slot, key in section.bindings.items():
            if slot not in slots or key not in fields:
                raise ValueError(f"{section.label}: bind valid slots to existing API fields.")
            limit = min(section.maxLength, 80 if slot == "heading" else 100 if slot == "reference" else 500 if slot == "recipient" else 160 if section.kind == "header" else 4000)
            if fields[key].maxLength > limit:
                raise ValueError(f"{section.label}: API field '{key}' must have a character limit of {limit} or less.")
        if section.bindings.get("text") and section.kind in ("text", "body"):
            indices = [i for i, key in enumerate(ids) if key == (section.id or section.kind)]
            rules = section.rules or template.rules
            if len(indices) != 1 or rules.minParagraphs != 1 or rules.maxParagraphs != 1 or rules.allowSubparagraphs or rules.allowSubsubparagraphs or any(i < len(template.defaults.subparagraphs) and template.defaults.subparagraphs[i] or i < len(template.defaults.subsubparagraphs) and any(template.defaults.subsubparagraphs[i]) for i in indices):
                raise ValueError(f"{section.label}: an API text block needs one paragraph, limits 1–1 and no nested clauses.")


def hydrate_api_document(value):
    if not isinstance(value, dict) or not isinstance(value.get("template"), dict):
        return value
    template = value["template"]
    if not isinstance(template.get("sections"), list) or not isinstance(template.get("apiFields", []), list):
        return value
    values = validate_api_values(value.get("apiValues", {}))
    fields = {f.key: f for f in (ApiField.model_validate(raw) for raw in template.get("apiFields", []))}
    result = hydrate_inline_document(value, template, fields, values, resolve_api_field)
    result = {**result}
    sections = [s for s in template["sections"] if isinstance(s, dict)]
    content_sections = [s for s in sections if s.get("kind") in ("text", "body")]
    primary = next((s.get("id") or s["kind"] for s in content_sections if s.get("kind") == "body"), (content_sections[0].get("id") or content_sections[0]["kind"]) if content_sections else "body")
    paragraphs = result.get("paragraphs", [])
    ids = value.get("paragraphSectionIds") or [primary] * (len(paragraphs) if isinstance(paragraphs, list) else 0)
    for section in sections:
        bindings = section.get("bindings") or {}
        if not isinstance(bindings, dict):
            continue
        for slot, key in bindings.items():
            if not isinstance(key, str) or key not in fields:
                continue
            resolved = resolve_api_field(fields[key], values)
            kind, section_id = section.get("kind"), section.get("id") or section.get("kind")
            if slot == "heading" or kind == "footer":
                old_text = result.get("text") or {}
                if not isinstance(old_text, dict):
                    continue
                result["text"] = {**old_text}
                if slot == "heading":
                    old_headings = old_text.get("sectionHeadings") or {}
                    if isinstance(old_headings, dict):
                        result["text"]["sectionHeadings"] = {**old_headings, section_id: resolved}
                else:
                    result["text"]["footer"] = resolved
            elif slot in ("reference", "recipient"):
                result[slot] = resolved
            elif kind in ("header", "title"):
                result["organization" if kind == "header" else "title"] = resolved
            elif kind == "closing":
                if SIGNATURE_MARKER.search(resolved):
                    raise ValueError("API closing text cannot define signature lines. Configure them in the template.")
                defaults = template.get("defaults", {})
                marker = SIGNATURE_MARKER.search(defaults.get("closing_text", "")) if isinstance(defaults, dict) and isinstance(defaults.get("closing_text", ""), str) else None
                result["closing_text"] = resolved + ("\n\n{{signature}}" if marker else "")
            elif kind in ("text", "body") and isinstance(paragraphs, list) and isinstance(ids, list):
                result["paragraphs"] = list(result.get("paragraphs", paragraphs))
                for index, owner in enumerate(ids):
                    if owner == section_id and index < len(result["paragraphs"]):
                        result["paragraphs"][index] = resolved
    return resolve_implicit_footer(result, value, template, fields, values, resolve_api_field)
