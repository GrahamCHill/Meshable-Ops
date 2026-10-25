import { sameInlineApiFields } from "../editor/inlineApi";
import { replaceClosingSegment, splitSignatureText } from "./signatureText";
import type { DocumentTemplate, TemplateSection } from "./templates";

export type TemplatePageTextSlot = "organization" | "title" | "reference" | "recipient" | "footer" | "heading" | "referenceLabel" | "recipientLabel" | "closingBefore" | "closingAfter";

export function pageTextBinding(section: TemplateSection, slot: TemplatePageTextSlot) {
  return slot === "referenceLabel" || slot === "recipientLabel" ? undefined
    : slot === "reference" || slot === "recipient" || slot === "heading" ? section.bindings?.[slot] : section.bindings?.text;
}

export function templatePageText(template: DocumentTemplate, section: TemplateSection, slot: TemplatePageTextSlot): string {
  const defaults = template.defaults;
  if (slot === "closingBefore" || slot === "closingAfter") return splitSignatureText(defaults.closing_text)[slot === "closingBefore" ? "before" : "after"];
  if (slot === "heading") {
    const id = section.id || section.kind, headings = defaults.text?.sectionHeadings;
    return headings && Object.prototype.hasOwnProperty.call(headings, id) ? headings[id] : section.label;
  }
  if (slot === "footer") {
    const organizationKey = template.sections.find((item) => item.kind === "header")?.bindings?.text;
    const referenceKey = template.sections.find((item) => item.kind === "details")?.bindings?.reference;
    return defaults.text?.footer ?? `${organizationKey ? `{{api:${organizationKey}}}` : defaults.organization} · ${referenceKey ? `{{api:${referenceKey}}}` : defaults.reference}`;
  }
  if (slot === "referenceLabel" || slot === "recipientLabel") return defaults.text?.[slot] ?? (slot === "referenceLabel" ? "Reference:" : "Prepared for:");
  return defaults[slot];
}

/** Save source wording only; preview values and signature markers never replace it. */
export function updateTemplatePageText(template: DocumentTemplate, section: TemplateSection, slot: TemplatePageTextSlot, text: string): DocumentTemplate {
  const original = templatePageText(template, section, slot);
  if (pageTextBinding(section, slot) || original === text || !sameInlineApiFields(original, text)) return template;
  const limit = slot === "heading" || slot.endsWith("Label") ? 80 : Math.min(section.maxLength, slot === "reference" ? 100 : slot === "recipient" ? 500 : slot === "organization" ? 160 : 4000);
  if (text.length > limit) return template;
  const defaults = template.defaults;
  if (slot === "closingBefore" || slot === "closingAfter") {
    const closing = replaceClosingSegment(defaults.closing_text, slot === "closingBefore" ? "before" : "after", text);
    if (closing.length > section.maxLength) return template;
    return { ...template, defaults: { ...defaults, closing_text: closing } };
  }
  if (slot === "heading") return { ...template, defaults: { ...defaults, text: { ...defaults.text, sectionHeadings: { ...defaults.text?.sectionHeadings, [section.id || section.kind]: text } } } };
  if (slot === "footer" || slot === "referenceLabel" || slot === "recipientLabel") return { ...template, defaults: { ...defaults, text: { ...defaults.text, [slot]: text } } };
  return { ...template, defaults: { ...defaults, [slot]: text } };
}
