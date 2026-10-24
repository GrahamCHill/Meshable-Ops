import { apiFieldErrors, isApiFields, resolveApiField, type PdfEditorApiValues } from "../editor/index";
import type { CustomDocument } from "./document";
import type { DocumentTemplate, TemplateSection } from "./templates";
import { isContentSection, sectionId, sectionIds, sectionRules } from "./paragraphSections";
import { signatureMarker } from "./media";
import { inlineEntries } from "./inlineFields";
import { inlineApiKeys } from "../editor/index";

export type ApiBindings = Partial<Record<"text" | "heading" | "reference" | "recipient", string>>;
export function bindingSlots(section: TemplateSection) {
  return section.kind === "details" ? ["reference", "recipient"] as const : isContentSection(section) ? ["text", "heading"] as const : ["text"] as const;
}
export function bindingLimit(section: TemplateSection, slot: string) {
  return Math.min(section.maxLength, slot === "heading" ? 80 : slot === "reference" ? 100 : slot === "recipient" ? 500 : section.kind === "header" ? 160 : 4000);
}
export function apiBindingErrors(template: DocumentTemplate) {
  const errors: string[] = [];
  if (!isApiFields(template.apiFields || [])) return ["API field definitions are invalid."];
  for (const section of template.sections) {
    const bindings = section.bindings;
    if (bindings === undefined) continue;
    if (!bindings || typeof bindings !== "object" || Array.isArray(bindings) || Object.entries(bindings).some(([slot, key]) => !(bindingSlots(section) as readonly string[]).includes(slot) || typeof key !== "string" || !template.apiFields?.some((field) => field.key === key))) {
      errors.push(`${section.label}: choose an existing API field for each valid binding.`); continue;
    }
    if (bindings.text && isContentSection(section)) {
      const indices = sectionIds(template.defaults, template).flatMap((id, i) => id === sectionId(section) ? [i] : []), rules = sectionRules(template, section);
      if (indices.length !== 1 || rules.minParagraphs !== 1 || rules.maxParagraphs !== 1 || rules.allowSubparagraphs || rules.allowSubsubparagraphs || indices.some((i) => template.defaults.subparagraphs[i]?.length || template.defaults.subsubparagraphs?.[i]?.flat().length)) errors.push(`${section.label}: an API text block must contain one paragraph, with limits 1–1 and no nested clauses.`);
    }
    for (const [slot, key] of Object.entries(bindings)) {
      const field = template.apiFields?.find((f) => f.key === key), limit = bindingLimit(section, slot);
      if (field && field.maxLength > limit) errors.push(`${section.label}: API field '${key}' must have a character limit of ${limit} or less.`);
    }
  }
  return errors;
}
export function applyApiValues(document: CustomDocument, apiValues: PdfEditorApiValues = document.apiValues || {}): CustomDocument {
  if (!document.template.sections.some((s) => Object.keys(s.bindings || {}).length)) return document.apiValues === apiValues || !document.apiValues && !Object.keys(apiValues).length ? document : { ...document, apiValues };
  const next: CustomDocument = { ...document, apiValues, paragraphs: [...document.paragraphs], text: { ...document.text, sectionHeadings: { ...document.text?.sectionHeadings } } };
  const ids = sectionIds(document, document.template);
  for (const section of document.template.sections) for (const [slot, key] of Object.entries(section.bindings || {})) {
    const field = document.template.apiFields?.find((f) => f.key === key);
    if (!field) continue;
    const { value } = resolveApiField(field, apiValues);
    if (slot === "heading") next.text!.sectionHeadings![sectionId(section)] = value;
    else if (slot === "reference" || slot === "recipient") next[slot] = value;
    else if (section.kind === "header") next.organization = value;
    else if (section.kind === "title") next.title = value;
    else if (section.kind === "footer") next.text!.footer = value;
    else if (section.kind === "closing") next.closing_text = value + (signatureMarker(document.template.defaults.closing_text) ? "\n\n{{signature}}" : "");
    else if (isContentSection(section)) { const index = ids.indexOf(sectionId(section)); if (index >= 0) next.paragraphs[index] = value; }
  }
  return next;
}
export function documentApiErrors(document: CustomDocument) {
  const keys = new Set([...document.template.sections.flatMap((s) => Object.values(s.bindings || {})), ...inlineEntries(document.template, document).flatMap((entry) => inlineApiKeys(entry.value))]);
  const errors = apiFieldErrors(document.template.apiFields?.filter((f) => keys.has(f.key)), document.apiValues || {});
  for (const section of document.template.sections) if (section.kind === "closing" && section.bindings?.text) {
    const field = document.template.apiFields?.find((f) => f.key === section.bindings!.text);
    if (field && signatureMarker(resolveApiField(field, document.apiValues).value)) errors.push(`${section.label}: API text cannot define signature lines. Configure them in the template.`);
  }
  return errors;
}
