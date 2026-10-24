import type { PdfEditorContent, PdfEditorParagraphSection, PdfEditorTextTarget } from "../editor/index";
import { clauseReference } from "../editor/index";
import type { CustomDocument } from "./document";
import type { DocumentTemplate, TemplateSection } from "./templates";

export const sectionId = (section: TemplateSection) => section.id || section.kind;
export const isContentSection = (section?: Pick<TemplateSection, "kind"> | null) => section?.kind === "body" || section?.kind === "text";
export const bodySections = (template: DocumentTemplate) => template.sections.filter(isContentSection);
export const primarySection = (template: DocumentTemplate) => bodySections(template).find((s) => s.kind === "body") || bodySections(template)[0];
export const sectionRules = (template: DocumentTemplate, section: TemplateSection) => ({ ...template.rules, ...section.rules, ...(section.kind === "text" ? { allowSubparagraphs: false, allowSubsubparagraphs: false } : {}) });
export const sectionIds = (content: PdfEditorContent, template: DocumentTemplate) => content.paragraphSectionIds || content.paragraphs.map(() => sectionId(primarySection(template)));
export function validSectionMembership(content: PdfEditorContent, template: DocumentTemplate) {
  const ids = content.paragraphSectionIds;
  return ids === undefined || Array.isArray(ids) && ids.length === content.paragraphs.length && ids.every((id) => typeof id === "string" && bodySections(template).some((s) => sectionId(s) === id));
}
export function sectionContent(content: PdfEditorContent, template: DocumentTemplate, id: string): PdfEditorContent {
  const ids = sectionIds(content, template), indices = content.paragraphs.flatMap((_, i) => ids[i] === id ? [i] : []);
  return { paragraphs: indices.map((i) => content.paragraphs[i]), paragraphSectionIds: indices.map(() => id),
    subparagraphs: indices.map((i) => content.subparagraphs?.[i] || []), subsubparagraphs: indices.map((i) => content.subsubparagraphs?.[i] || []),
    paragraphStyles: indices.map((i) => content.paragraphStyles?.[i] || {}), subparagraphStyles: indices.map((i) => content.subparagraphStyles?.[i] || []), subsubparagraphStyles: indices.map((i) => content.subsubparagraphStyles?.[i] || []) };
}
/** Sort whole paragraph families into template order without detaching styles or descendants. */
export function orderedContent<T extends PdfEditorContent>(content: T, template: DocumentTemplate): T {
  const ids = sectionIds(content, template), order = bodySections(template).flatMap((s) => ids.flatMap((id, i) => id === sectionId(s) ? [i] : []));
  if (order.length !== content.paragraphs.length || content.paragraphSectionIds && order.every((i, j) => i === j)) return content;
  return { ...content, paragraphs: order.map((i) => content.paragraphs[i]), paragraphSectionIds: order.map((i) => ids[i]),
    subparagraphs: order.map((i) => content.subparagraphs?.[i] || []), subsubparagraphs: order.map((i) => content.subsubparagraphs?.[i] || []),
    paragraphStyles: order.map((i) => content.paragraphStyles?.[i] || {}), subparagraphStyles: order.map((i) => content.subparagraphStyles?.[i] || []), subsubparagraphStyles: order.map((i) => content.subsubparagraphStyles?.[i] || []) };
}
export function normalizeParagraphTemplate(template: DocumentTemplate): DocumentTemplate {
  const defaults = orderedContent(template.defaults, template);
  const needsImages = template.images?.some((image) => (image.section === "body" || image.section === "text") && !image.sectionId);
  const needsLabels = template.sections.some((s) => s.kind === "body" && /^Paragraphs(?: \d+)?$/.test(s.label));
  if (defaults === template.defaults && !needsImages && !needsLabels) return template;
  return { ...template, defaults, ...(needsLabels ? { sections: template.sections.map((s) => s.kind === "body" && /^Paragraphs(?: \d+)?$/.test(s.label) ? { ...s, label: s.label.replace("Paragraphs", "Terms") } : s) } : {}),
    ...(needsImages ? { images: template.images?.map((image) => (image.section === "body" || image.section === "text") && !image.sectionId ? { ...image, sectionId: sectionId(bodySections(template).find((s) => s.kind === image.section) || primarySection(template)) } : image) } : {}) };
}
export function editorParagraphSections(document: CustomDocument): PdfEditorParagraphSection[] {
  return bodySections(document.template).map((s) => { const rules = sectionRules(document.template, s); return { id: sectionId(s), label: s.label, ...rules,
    maxSubparagraphs: rules.allowSubparagraphs ? 26 : 0, maxSubsubparagraphs: rules.allowSubparagraphs && rules.allowSubsubparagraphs !== false ? 26 : 0,
    maxTextLength: s.maxLength, editable: !s.locked && !s.bindings?.text, apiFieldKey: s.bindings?.text, presentation: s.kind === "text" ? "text" : "clauses", numbered: s.kind !== "text" && (s.numbered ?? document.numbered), style: s.style }; });
}
export function targetSection(document: CustomDocument, index = 0) {
  return bodySections(document.template).find((s) => sectionId(s) === sectionIds(document, document.template)[index]) || bodySections(document.template)[0];
}
export function documentClauseLabel(document: CustomDocument, target: PdfEditorTextTarget) {
  const section = targetSection(document, target.index), ids = sectionIds(document, document.template);
  const index = ids.slice(0, target.index).filter((id) => id === sectionId(section)).length;
  const kind = target.subsubIndex !== undefined ? "Subsubclause" : target.subIndex !== undefined ? "Subparagraph" : "Paragraph";
  return `${bodySections(document.template).length > 1 ? `${section.label} — ` : ""}${kind} ${clauseReference({ ...target, index })}`;
}
