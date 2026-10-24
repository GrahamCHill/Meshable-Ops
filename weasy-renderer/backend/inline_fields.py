"""Inline markers remain immutable source identities; API values are resolved once as plain text."""
import copy
import re
from collections import defaultdict

from backend.media import SIGNATURE_MARKER

TOKEN = re.compile(r"\{\{api:([a-zA-Z][a-zA-Z0-9_.-]{0,119})\}\}")


def source_limit(template, scope):
    if scope.startswith("heading:") or scope.endswith("Label"):
        return 80
    sections = [s for s in template.get("sections", []) if isinstance(s, dict)]
    section = next((s for s in sections if (s.get("id") or s.get("kind")) == scope[8:]), {}) if scope.startswith("content:") else next((s for s in sections if s.get("kind") == ("details" if scope in ("reference", "recipient") else scope)), {})
    limit = section.get("maxLength", 4000)
    return min(limit if isinstance(limit, int) else 4000, 160 if scope == "header" else 100 if scope == "reference" else 500 if scope == "recipient" else 4000)


def inline_entries(template, source):
    entries, sections = [], [section for section in template.get("sections", []) if isinstance(section, dict)]
    content = [s for s in sections if s.get("kind") in ("text", "body")]
    primary = next((s.get("id") or s["kind"] for s in content if s["kind"] == "body"), (content[0].get("id") or content[0]["kind"]) if content else "body")
    paragraphs = source.get("paragraphs", [])
    if not isinstance(paragraphs, list):
        return []
    ids = source.get("paragraphSectionIds") or [primary] * len(paragraphs)
    if not isinstance(ids, list):
        return []
    text = source.get("text") or {}
    if not isinstance(text, dict):
        text = {}
    def add(scope, value, path=None):
        if isinstance(value, str):
            entries.append((scope, value, path))
    for section in sections:
        kind, key, bindings = section.get("kind"), section.get("id") or section.get("kind"), section.get("bindings") or {}
        if kind not in ("header", "title", "details", "text", "body", "closing", "footer") or not isinstance(key, str):
            continue
        if not isinstance(bindings, dict):
            continue
        if kind in ("text", "body"):
            if not bindings.get("text"):
                children, leaves = source.get("subparagraphs") or [], source.get("subsubparagraphs") or []
                if not isinstance(children, list) or not isinstance(leaves, list):
                    continue
                for i, value in enumerate(paragraphs):
                    if i >= len(ids) or ids[i] != key:
                        continue
                    add(f"content:{key}", value, ["paragraphs", i])
                    if i < len(children) and isinstance(children[i], list):
                        for j, value in enumerate(children[i]):
                            add(f"content:{key}", value, ["subparagraphs", i, j])
                    if i < len(leaves) and isinstance(leaves[i], list):
                        for j, row in enumerate(leaves[i]):
                            if isinstance(row, list):
                                for k, value in enumerate(row):
                                    add(f"content:{key}", value, ["subsubparagraphs", i, j, k])
            if not bindings.get("heading") and len(content) > 1:
                headings = text.get("sectionHeadings") or {}
                if isinstance(headings, dict):
                    add(f"heading:{key}", headings.get(key, section.get("label", "")), ["text", "sectionHeadings", key])
        elif kind == "details":
            for name in ("reference", "recipient"):
                if not bindings.get(name):
                    add(name, source.get(name, ""), [name])
            add("referenceLabel", text.get("referenceLabel", "Reference:"), ["text", "referenceLabel"])
            add("recipientLabel", text.get("recipientLabel", "Prepared for:"), ["text", "recipientLabel"])
        elif not bindings.get("text"):
            if kind == "footer":
                add("footer", text.get("footer", f'{source.get("organization", "")} · {source.get("reference", "")}'), ["text", "footer"] if text.get("footer") is not None else None)
            else:
                name = "organization" if kind == "header" else "closing_text" if kind == "closing" else "title"
                add(kind, source.get(name, ""), [name])
    return entries


def validate_inline_template(template):
    raw = template.model_dump(exclude_none=True)
    keys = {field.key for field in template.apiFields}
    for scope, source, _ in inline_entries(raw, raw["defaults"]):
        if TOKEN.search(source) and len(source) > source_limit(raw, scope):
            raise ValueError(f"{scope}: template source exceeds its character limit.")
        for key in TOKEN.findall(source):
            if key not in keys:
                raise ValueError(f"{scope}: unknown inline API field '{key}'.")
    content = [s for s in template.sections if s.kind in ("text", "body")]
    primary = next((s.id or s.kind for s in content if s.kind == "body"), content[0].id or content[0].kind)
    ids = template.defaults.paragraphSectionIds or [primary] * len(template.defaults.paragraphs)
    for section in content:
        rules = section.rules or template.rules
        indices = [i for i, key in enumerate(ids) if key == (section.id or section.kind)]
        discarded = []
        for at, i in enumerate(indices):
            if at >= rules.maxParagraphs:
                discarded.append(template.defaults.paragraphs[i])
            if not rules.allowSubparagraphs and i < len(template.defaults.subparagraphs):
                discarded += template.defaults.subparagraphs[i]
            if (not rules.allowSubparagraphs or not rules.allowSubsubparagraphs) and i < len(template.defaults.subsubparagraphs):
                discarded += [text for row in template.defaults.subsubparagraphs[i] for text in row]
        if any(TOKEN.search(text) for text in discarded):
            raise ValueError(f"{section.label}: paragraph rules would discard inline API fields.")


def hydrate_inline_document(document, template, fields, values, resolve):
    defaults = template.get("defaults")
    if not isinstance(defaults, dict):
        return document
    def signature(source):
        groups = defaultdict(list)
        for scope, text, _ in inline_entries(template, source):
            groups[scope] += TOKEN.findall(text)
        return {scope: sorted(keys) if scope.startswith("content:") else keys for scope, keys in groups.items()}
    expected, actual = signature(defaults), signature(document)
    for scope in expected.keys() | actual.keys():
        if expected.get(scope, []) != actual.get(scope, []):
            raise ValueError(f"{scope}: protected inline API fields must be preserved. Change placement in the template designer.")
    entries = inline_entries(template, document)
    if not any(TOKEN.search(text) for _, text, _ in entries):
        return document
    result = copy.deepcopy(document)
    for scope, source, path in entries:
        if TOKEN.search(source) and len(source) > source_limit(template, scope):
            raise ValueError(f"{scope}: source text exceeds its character limit.")
        def replace(match):
            key = match[1]
            if key not in fields:
                raise ValueError(f"{scope}: unknown inline API field '{key}'.")
            value = resolve(fields[key], values)
            if scope == "closing" and SIGNATURE_MARKER.search(value):
                raise ValueError("Closing: inline API values cannot define signature lines.")
            return value
        resolved = TOKEN.sub(replace, source)
        if path is not None and TOKEN.search(source):
            target = result
            for key in path[:-1]:
                if isinstance(target, dict):
                    if target.get(key) is None:
                        target[key] = {}
                target = target[key]
            target[path[-1]] = resolved
    return result


def resolve_implicit_footer(result, source, template, fields, values, resolve):
    text = source.get("text") or {}
    if not isinstance(text, dict) or text.get("footer") is not None:
        return result
    sections = [s for s in template.get("sections", []) if isinstance(s, dict)]
    if not any(s.get("kind") == "footer" and not (s.get("bindings") or {}).get("text") for s in sections):
        return result
    if not any(isinstance(source.get(key), str) and TOKEN.search(source[key]) for key in ("organization", "reference")):
        return result
    def component(key, kind, slot):
        if any(s.get("kind") == kind and (s.get("bindings") or {}).get(slot) for s in sections):
            return result.get(key, "")
        return TOKEN.sub(lambda match: resolve(fields[match[1]], values), source.get(key, ""))
    result["text"] = {**(result.get("text") or {}), "footer": f'{component("organization", "header", "text")} · {component("reference", "details", "reference")}'}
    return result
