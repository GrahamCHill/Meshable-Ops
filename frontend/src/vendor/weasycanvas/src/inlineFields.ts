import { inlineApiKeys, resolveInlineApiText } from "../editor/index";
import type { CustomDocument } from "./document";
import type { DocumentTemplate } from "./templates";
import { bodySections, isContentSection, sectionId, sectionIds, sectionRules } from "./paragraphSections";
import { footerText, headingText } from "./documentText";
import { signatureMarker } from "./media";

type TextSource = DocumentTemplate["defaults"];
type Entry = { scope: string; value: string; path?: (string | number)[] };
function sourceLimit(template: DocumentTemplate, scope: string) {
  if (scope.startsWith("heading:") || scope.endsWith("Label")) return 80;
  if (scope === "reference") return Math.min(100, template.sections.find((s) => s.kind === "details")?.maxLength || 100);
  if (scope === "recipient") return Math.min(500, template.sections.find((s) => s.kind === "details")?.maxLength || 500);
  const section = template.sections.find((s) => scope.startsWith("content:") ? sectionId(s) === scope.slice(8) : s.kind === scope);
  return Math.min(scope === "header" ? 160 : 4000, section?.maxLength || 4000);
}
export function inlineEntries(template: DocumentTemplate, source: TextSource): Entry[] {
  const entries: Entry[] = [], ids = sectionIds(source, template);
  for (const section of template.sections) {
    const id = sectionId(section), bindings = section.bindings || {};
    const add = (scope: string, value: string, path?: (string | number)[]) => entries.push({ scope, value, path });
    if (isContentSection(section)) {
      if (!bindings.text) source.paragraphs.forEach((value, i) => { if (ids[i] !== id) return;
        add(`content:${id}`, value, ["paragraphs", i]);
        source.subparagraphs[i]?.forEach((value, j) => add(`content:${id}`, value, ["subparagraphs", i, j]));
        source.subsubparagraphs?.[i]?.forEach((row, j) => row.forEach((value, k) => add(`content:${id}`, value, ["subsubparagraphs", i, j, k])));
      });
      if (!bindings.heading && bodySections(template).length > 1) add(`heading:${id}`, headingText(source, section), ["text", "sectionHeadings", id]);
    } else if (section.kind === "details") {
      for (const key of ["reference", "recipient"] as const) if (!bindings[key]) add(key, source[key], [key]);
      add("referenceLabel", source.text?.referenceLabel ?? "Reference:", ["text", "referenceLabel"]);
      add("recipientLabel", source.text?.recipientLabel ?? "Prepared for:", ["text", "recipientLabel"]);
    } else if (!bindings.text) {
      if (section.kind === "footer") add("footer", footerText(source), source.text?.footer !== undefined ? ["text", "footer"] : undefined);
      else { const key = section.kind === "header" ? "organization" : section.kind === "closing" ? "closing_text" : "title"; add(section.kind, source[key], [key]); }
    }
  }
  return entries;
}
export function inlineTemplateErrors(template: DocumentTemplate) {
  const errors: string[] = [], keys = new Set(template.apiFields?.map((f) => f.key));
  for (const entry of inlineEntries(template, template.defaults)) {
    const used = inlineApiKeys(entry.value);
    for (const key of used) if (!keys.has(key)) errors.push(`${entry.scope}: unknown inline API field '${key}'.`);
    if (used.length && entry.value.length > sourceLimit(template, entry.scope)) errors.push(`${entry.scope}: template source exceeds its character limit.`);
  }
  for (const section of bodySections(template)) {
    const rules = sectionRules(template, section), indices = sectionIds(template.defaults, template).flatMap((id, i) => id === sectionId(section) ? [i] : []);
    const discarded = indices.flatMap((i, at) => [...(at >= rules.maxParagraphs ? [template.defaults.paragraphs[i]] : []), ...(!rules.allowSubparagraphs ? template.defaults.subparagraphs[i] || [] : []), ...(!rules.allowSubparagraphs || !rules.allowSubsubparagraphs ? template.defaults.subsubparagraphs?.[i]?.flat() || [] : [])]);
    if (discarded.some((text) => inlineApiKeys(text).length)) errors.push(`${section.label}: paragraph rules would discard inline API fields. Keep those clauses enabled.`);
  }
  return [...new Set(errors)];
}
export function inlineDocumentErrors(document: CustomDocument) {
  const signature = (source: TextSource) => {
    const groups = new Map<string, string[]>();
    for (const entry of inlineEntries(document.template, source)) groups.set(entry.scope, [...(groups.get(entry.scope) || []), ...inlineApiKeys(entry.value)]);
    return new Map([...groups].map(([scope, keys]) => [scope, JSON.stringify(scope.startsWith("content:") ? keys.sort() : keys)]));
  };
  const expected = signature(document.template.defaults), actual = signature(document);
  const errors = [...new Set([...expected.keys(), ...actual.keys()])].filter((scope) => (expected.get(scope) || "[]") !== (actual.get(scope) || "[]")).map((scope) => `${scope}: protected inline API fields must be preserved. Change field placement in the template designer.`);
  for (const entry of inlineEntries(document.template, document)) if (inlineApiKeys(entry.value).length && entry.value.length > sourceLimit(document.template, entry.scope)) errors.push(`${entry.scope}: source text exceeds its character limit.`);
  const fields = document.template.apiFields || [];
  for (const entry of inlineEntries(document.template, document)) if (entry.scope === "closing") for (const key of inlineApiKeys(entry.value)) {
    const field = fields.find((f) => f.key === key), raw = document.apiValues?.[key], value = raw === null || raw === undefined ? field?.fallback || "" : String(raw);
    if (signatureMarker(value)) errors.push("Closing: inline API values cannot define signature lines.");
  }
  return errors;
}
/** A disposable resolved copy for character limits and page margins; never put it in editor state. */
export function resolvedInlineDocument(document: CustomDocument): CustomDocument {
  const next: CustomDocument = { ...document, text: { ...document.text, sectionHeadings: { ...document.text?.sectionHeadings } }, paragraphs: [...document.paragraphs], subparagraphs: document.subparagraphs.map((row) => [...row]), subsubparagraphs: document.subsubparagraphs.map((row) => row.map((values) => [...values])) };
  for (const entry of inlineEntries(document.template, document)) if (entry.path && inlineApiKeys(entry.value).length) {
    let target: any = next; for (const key of entry.path.slice(0, -1)) target = target[key] ??= {};
    target[entry.path[entry.path.length - 1]] = resolveInlineApiText(entry.value, document.template.apiFields, document.apiValues);
  }
  if (document.text?.footer === undefined && inlineApiKeys(footerText(document)).length && document.template.sections.some((s) => s.kind === "footer" && !s.bindings?.text)) {
    const resolve = (key: "organization" | "reference", bound?: string) => bound ? document[key] : resolveInlineApiText(document[key], document.template.apiFields, document.apiValues);
    next.text!.footer = `${resolve("organization", document.template.sections.find((s) => s.kind === "header")?.bindings?.text)} · ${resolve("reference", document.template.sections.find((s) => s.kind === "details")?.bindings?.reference)}`;
  }
  return next;
}
export function renameInlineField(template: DocumentTemplate, from: string, to: string): DocumentTemplate {
  const replace = (value: any): any => typeof value === "string" ? value.split(`{{api:${from}}}`).join(`{{api:${to}}}`) : Array.isArray(value) ? value.map(replace) : value && typeof value === "object" ? Object.fromEntries(Object.entries(value).map(([key, item]) => [key, replace(item)])) : value;
  return { ...template, defaults: replace(template.defaults), sections: template.sections.map((s) => ({ ...s, label: replace(s.label) })) };
}
