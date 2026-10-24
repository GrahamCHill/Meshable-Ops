import type { CustomDocument } from "./document";
import { type PdfEditorApiField, type PdfEditorApiValues, type PdfEditorTextStyle } from "../editor/index";
import { apiBindingErrors, applyApiValues, documentApiErrors, type ApiBindings } from "./apiBindings";
import { isImages, isSignatures, isSignatureSettingsList, mediaMargins, pageText, signatureFields, signatureMarker, type DocumentImage, type SignatureSettings } from "./media";
import { migrateSignatureTemplate } from "./signatureMigration";
import { bodySections, isContentSection, sectionId, sectionRules, sectionContent, normalizeParagraphTemplate, validSectionMembership } from "./paragraphSections";
import { extraDocumentText, isDocumentText, type DocumentText } from "./documentText";
import { inlineTemplateErrors, inlineDocumentErrors, resolvedInlineDocument } from "./inlineFields";

export type SectionKind = "header" | "title" | "details" | "text" | "body" | "closing" | "footer";
export type ParagraphRules = { minParagraphs: number; maxParagraphs: number; allowSubparagraphs: boolean; allowSubsubparagraphs?: boolean };
export type TemplateSection = { id?: string; kind: SectionKind; label: string; required: boolean; locked: boolean; maxLength: number; rules?: ParagraphRules; style?: PdfEditorTextStyle; numbered?: boolean; bindings?: ApiBindings };
export type PageSettings = { size: "A4" | "Letter"; orientation: "portrait" | "landscape"; marginTop: number; marginRight: number; marginBottom: number; marginLeft: number; paragraphGap: number };
export type DocumentTemplate = {
  version: 1; id: string; name: string; description: string;
  sections: TemplateSection[];
  rules: ParagraphRules;
  layout: PageSettings;
  formatting: { body: PdfEditorTextStyle; title: PdfEditorTextStyle; closing: PdfEditorTextStyle };
  images?: DocumentImage[];
  signatures?: SignatureSettings[];
  apiFields?: PdfEditorApiField[];
  defaults: { text?: DocumentText; organization: string; title: string; reference: string; recipient: string; paragraphs: string[]; paragraphSectionIds?: string[]; subparagraphs: string[][]; subsubparagraphs?: string[][][]; closing_text: string;
    paragraphStyles?: PdfEditorTextStyle[]; subparagraphStyles?: PdfEditorTextStyle[][]; subsubparagraphStyles?: PdfEditorTextStyle[][][]; numbered?: boolean };
};
export const sectionCatalog: { kind: SectionKind; label: string; description: string; icon: string }[] = [
  { kind: "header", label: "Header", description: "Organisation on every page", icon: "▔" },
  { kind: "title", label: "Document title", description: "An editable document heading", icon: "T" },
  { kind: "details", label: "Document details", description: "Reference and recipient", icon: "≡" },
  { kind: "text", label: "Paragraphs", description: "General text without numbering", icon: "¶" },
  { kind: "body", label: "Terms", description: "Numbered clauses and subclauses", icon: "§" },
  { kind: "closing", label: "Closing", description: "A final note or sign-off", icon: "✎" },
  { kind: "footer", label: "Footer", description: "Organisation and reference", icon: "▁" },
];
export function newSection(kind: SectionKind): TemplateSection {
  return { kind, label: sectionCatalog.find((s) => s.kind === kind)!.label, required: true, locked: false, maxLength: kind === "header" ? 160 : 4000,
    ...(kind === "text" ? { numbered: false, rules: { minParagraphs: 1, maxParagraphs: 100, allowSubparagraphs: false, allowSubsubparagraphs: false } } : {}) };
}
export const defaultTemplate: DocumentTemplate = {
  version: 1, id: "standard", name: "Standard document", description: "A structured document with all your familiar sections.",
  sections: sectionCatalog.filter(({ kind }) => kind !== "text").map(({ kind }) => newSection(kind)),
  rules: { minParagraphs: 1, maxParagraphs: 100, allowSubparagraphs: true, allowSubsubparagraphs: true },
  layout: { size: "A4", orientation: "portrait", marginTop: 30, marginRight: 20, marginBottom: 25, marginLeft: 20, paragraphGap: 18 },
  formatting: { body: { fontFamily: "Arial", fontSize: 11, lineHeight: 1.5, align: "left", color: "#17252f" }, title: { fontSize: 20, bold: true, lineHeight: 1.2 }, closing: {} },
  defaults: { organization: "Example organisation", title: "Sample document", reference: "DOC-001", recipient: "Example recipient",
    paragraphs: ["This is an editable sample paragraph. Replace it with your own content.", "Add, reorder, or remove paragraphs to customize this document."],
    subparagraphs: [["This is the first example subparagraph.", "This is the second example subparagraph."], []], closing_text: "End of document." },
};
export function clone<T>(value: T): T { return JSON.parse(JSON.stringify(value)) as T; }
export function createDocumentFromTemplate(template: DocumentTemplate, apiValues: PdfEditorApiValues = {}): CustomDocument {
  const copy = clone(normalizeParagraphTemplate(migrateSignatureTemplate(template) as DocumentTemplate));
  const parts = bodySections(copy).map((section) => {
    const rules = sectionRules(copy, section), content = sectionContent(copy.defaults, copy, sectionId(section));
    const count = Math.max(rules.minParagraphs, Math.min(rules.maxParagraphs, content.paragraphs.length));
    return { paragraphs: Array.from({ length: count }, (_, i) => content.paragraphs[i] || "New paragraph."), paragraphSectionIds: Array.from({ length: count }, () => sectionId(section)),
      subparagraphs: Array.from({ length: count }, (_, i) => rules.allowSubparagraphs ? content.subparagraphs?.[i] || [] : []),
      subsubparagraphs: Array.from({ length: count }, (_, i) => rules.allowSubparagraphs && rules.allowSubsubparagraphs !== false ? content.subsubparagraphs?.[i] || [] : []),
      paragraphStyles: Array.from({ length: count }, (_, i) => content.paragraphStyles?.[i] || {}),
      subparagraphStyles: Array.from({ length: count }, (_, i) => rules.allowSubparagraphs ? content.subparagraphStyles?.[i] || [] : []),
      subsubparagraphStyles: Array.from({ length: count }, (_, i) => rules.allowSubparagraphs && rules.allowSubsubparagraphs !== false ? content.subsubparagraphStyles?.[i] || [] : []) };
  });
  return applyApiValues({ ...copy.defaults, paragraphs: parts.flatMap((p) => p.paragraphs), paragraphSectionIds: parts.flatMap((p) => p.paragraphSectionIds), subparagraphs: parts.flatMap((p) => p.subparagraphs), subsubparagraphs: parts.flatMap((p) => p.subsubparagraphs),
    template: copy, layout: clone(copy.layout), formatting: clone(copy.formatting),
    images: clone(copy.images || []), signatures: clone(signatureFields(copy.defaults.closing_text, copy.signatures)),
    paragraphStyles: parts.flatMap((p) => p.paragraphStyles), subparagraphStyles: parts.flatMap((p) => p.subparagraphStyles), subsubparagraphStyles: parts.flatMap((p) => p.subsubparagraphStyles), numbered: copy.defaults.numbered ?? true }, apiValues);
}
export function pageDimensions(layout: PageSettings) {
  const [width, height] = layout.size === "Letter" ? [215.9, 279.4] : [210, 297];
  return layout.orientation === "landscape" ? { width: height, height: width } : { width, height };
}
export function templateErrors(template: DocumentTemplate): string[] {
  const errors: string[] = [...apiBindingErrors(template), ...inlineTemplateErrors(template)];
  if (!template.name.trim()) errors.push("Give your template a name.");
  if (template.signatures && !isSignatureSettingsList(template.signatures)) errors.push("Use valid signature settings with unique field IDs starting with a letter (up to 100 letters, numbers, dots, underscores or hyphens).");
  if (!template.sections.some(isContentSection)) errors.push("Include a Paragraphs or Terms section.");
  if (bodySections(template).length > 10) errors.push("Use at most 10 Paragraphs and Terms sections in total.");
  for (const section of bodySections(template)) if (!isParagraphRules(sectionRules(template, section))) errors.push(`${section.label}: paragraph limits must be whole numbers between 1 and 100, with minimum no greater than maximum.`);
  for (const section of bodySections(template).filter((s) => s.kind === "text")) {
    const content = sectionContent(template.defaults, template, sectionId(section));
    if (section.numbered === true || section.rules?.allowSubparagraphs || section.rules?.allowSubsubparagraphs || content.subparagraphs?.some((row) => row.length) || content.subsubparagraphs?.flat(2).length) errors.push(`${section.label}: general text cannot contain numbering or nested clauses. Use a Terms section.`);
  }
  if (!validSectionMembership(template.defaults, template)) errors.push("Each paragraph must belong to an existing paragraph section.");
  if (!Number.isInteger(template.rules.minParagraphs) || !Number.isInteger(template.rules.maxParagraphs) || template.rules.minParagraphs < 1 || template.rules.maxParagraphs > 100 || template.rules.minParagraphs > template.rules.maxParagraphs) errors.push("Paragraph limits must be whole numbers between 1 and 100, with minimum no greater than maximum.");
  const { width, height } = pageDimensions(template.layout);
  if (width - template.layout.marginLeft - template.layout.marginRight < 80 || height - template.layout.marginTop - template.layout.marginBottom < 100) errors.push("Margins must leave at least 80 × 100 mm of writing space.");
  if (template.sections.some((s) => !s.label.trim() || !Number.isInteger(s.maxLength) || s.maxLength < 1 || s.maxLength > (s.kind === "header" ? 160 : 4000))) errors.push("Section labels and character limits must be valid.");
  if (template.defaults.text !== undefined && !isDocumentText(template.defaults.text)) errors.push("Default labels, headings or footer text are invalid.");
  if (template.defaults.text?.footer && template.defaults.text.footer.length > (template.sections.find((s) => s.kind === "footer")?.maxLength || 4000)) errors.push("Default footer exceeds the character limit.");
  const media = mediaMargins(template.layout, template.images, pageText(template.defaults.organization, template.defaults.reference, template.sections, template.defaults.text?.footer));
  if (height - media.top - media.bottom < 100) errors.push("Header/footer content leaves too little writing space. Reduce the text or image height.");
  if (template.images?.some((i) => !template.sections.some((s) => s.kind === i.section) || i.xMm + i.widthMm > width - template.layout.marginLeft - template.layout.marginRight + .1)) errors.push("Keep images within the margins of an existing section.");
  if (template.images?.some((i) => i.sectionId && !bodySections(template).some((s) => sectionId(s) === i.sectionId && s.kind === i.section))) errors.push("Each paragraph image must belong to an existing paragraph section.");
  if (template.signatures?.some((s) => s.required) && (!signatureMarker(template.defaults.closing_text) || !template.sections.some((s) => s.kind === "closing"))) errors.push("Add signature lines to the closing before requiring signatures.");
  if (bodySections(template).reduce((total, s) => { const rules = sectionRules(template, s); return total + Math.max(rules.minParagraphs, Math.min(rules.maxParagraphs, sectionContent(template.defaults, template, sectionId(s)).paragraphs.length)); }, 0) > 100) errors.push("Use at most 100 paragraphs across all sections.");
  for (const section of template.sections.filter((s) => s.locked)) {
    const content = isContentSection(section) ? sectionContent(template.defaults, template, sectionId(section)) : null, rules = sectionRules(template, section);
    const fields = section.kind === "header" ? [template.defaults.organization] : section.kind === "title" ? [template.defaults.title] : section.kind === "details" ? [template.defaults.reference, template.defaults.recipient] : section.kind === "closing" ? [template.defaults.closing_text] : content ? [...content.paragraphs.slice(0, rules.maxParagraphs), ...(rules.allowSubparagraphs ? (content.subparagraphs || []).slice(0, rules.maxParagraphs).flat() : []), ...(rules.allowSubparagraphs && rules.allowSubsubparagraphs !== false ? (content.subsubparagraphs || []).slice(0, rules.maxParagraphs).flat(2) : [])] : [];
    if (fields.some((s) => s.length > section.maxLength || (section.required && !s.trim()))) errors.push(`${section.label}: locked default text must meet its rules.`);
  }
  return errors;
}
export function documentErrors(source: CustomDocument): string[] {
  const errors = [...templateErrors(source.template), ...documentApiErrors(source), ...inlineDocumentErrors(source)];
  const value = resolvedInlineDocument(source);
  const margins = mediaMargins(value.layout, value.images, pageText(value.organization, value.reference, value.template.sections, value.text?.footer));
  const size = pageDimensions(value.layout);
  if (size.height - margins.top - margins.bottom < 100) errors.push("Header/footer content leaves too little writing space. Reduce the text or image height.");
  if (value.images.some((i) => i.xMm + i.widthMm > size.width - value.layout.marginLeft - value.layout.marginRight + .1)) errors.push("An image extends beyond the page margins. Resize it or move it left.");
  const signing = signatureFields(value.closing_text, value.signatures);
  for (let i = 0; i < Math.max(value.signatures.length, signing.length, value.template.signatures?.length || 0); i++) {
    if ((value.signatures[i]?.required || value.template.signatures?.[i]?.required) && !signing[i]?.dataUrl) errors.push(`Add the required signature ${i + 1} before exporting.`);
  }
  if (value.signatures.length > 4) errors.push("Use at most four signatures.");
  if (!isSignatures(value.signatures)) errors.push("Use valid signature values and unique signing field IDs.");
  const signatureRules = value.template.signatures || [];
  if (signatureRules.length && value.signatures.length && (signatureRules.length !== value.signatures.length || signatureRules.some((field, index) => field.id !== value.signatures[index]?.id))) errors.push("Signature field IDs and order must match the template's signing fields.");
  if (value.signatures.some((s) => s.dataUrl) && (!signing.length || !value.template.sections.some((s) => s.kind === "closing"))) errors.push("Signature images must be anchored to the closing.");
  for (const section of value.template.sections) {
    const content = isContentSection(section) ? sectionContent(value, value.template, sectionId(section)) : null;
    const fields = section.kind === "header" ? [value.organization] : section.kind === "title" ? [value.title] : section.kind === "details" ? [value.reference, value.recipient] : section.kind === "closing" ? [value.closing_text] : content ? [...content.paragraphs, ...(content.subparagraphs || []).flat(), ...(content.subsubparagraphs || []).flat(2)] : [];
    if (section.required && fields.some((field) => !field.trim())) errors.push(`${section.label}: fill in the required text.`);
    if (fields.some((field) => field.length > section.maxLength)) errors.push(`${section.label}: use at most ${section.maxLength} characters per field.`);
    if (content) {
      const rules = sectionRules(value.template, section);
      if (content.paragraphs.length < rules.minParagraphs || content.paragraphs.length > rules.maxParagraphs) errors.push(`${section.label}: use ${rules.minParagraphs}–${rules.maxParagraphs} paragraphs.`);
      if (!rules.allowSubparagraphs && content.subparagraphs?.some((row) => row.length)) errors.push(`${section.label}: subparagraphs are not allowed.`);
      if ((!rules.allowSubparagraphs || rules.allowSubsubparagraphs === false) && content.subsubparagraphs?.flat(2).length) errors.push(`${section.label}: subsubclauses are not allowed.`);
    }
  }
  if (!validSectionMembership(value, value.template)) errors.push("Each paragraph must belong to an existing paragraph section.");
  if (value.paragraphs.length > 100) errors.push("Use at most 100 paragraphs across all sections.");
  if (value.reference.length > 100 || value.recipient.length > 500) errors.push("Use at most 100 characters for the reference and 500 for the recipient.");
  if (value.text !== undefined && !isDocumentText(value.text)) errors.push("Document labels, headings or footer text are invalid.");
  if (value.text?.footer && value.text.footer.length > (value.template.sections.find((s) => s.kind === "footer")?.maxLength || 4000)) errors.push("Footer: text exceeds the template character limit.");
  if ([value.organization, value.title, value.reference, value.recipient, value.closing_text, ...value.paragraphs, ...value.subparagraphs.flat(), ...value.subsubparagraphs.flat(2), ...extraDocumentText(value.text)].join("").length > 60000) errors.push("Use at most 60,000 characters in a document.");
  return errors;
}
const finite = (n: unknown, min: number, max: number) => typeof n === "number" && Number.isFinite(n) && n >= min && n <= max;
const knownKeys = (value: object, keys: string[]) => Object.keys(value).every((key) => keys.includes(key));
const isParagraphRules = (rules: ParagraphRules) => !!rules && knownKeys(rules, ["minParagraphs", "maxParagraphs", "allowSubparagraphs", "allowSubsubparagraphs"]) && Number.isInteger(rules.minParagraphs) && Number.isInteger(rules.maxParagraphs) && finite(rules.minParagraphs, 1, 100) && finite(rules.maxParagraphs, rules.minParagraphs, 100) && typeof rules.allowSubparagraphs === "boolean" && (rules.allowSubsubparagraphs === undefined || typeof rules.allowSubsubparagraphs === "boolean");
export const isTextStyle = (input: unknown): input is PdfEditorTextStyle => {
  const s = input as PdfEditorTextStyle;
  return !!s && typeof s === "object" && !Array.isArray(s) && knownKeys(s, ["fontFamily", "fontSize", "bold", "italic", "underline", "align", "color", "lineHeight"]) &&
    (s.fontFamily === undefined || ["Arial", "Georgia", "Times New Roman", "Verdana", "Courier New"].includes(s.fontFamily)) &&
    (s.fontSize === undefined || finite(s.fontSize, 8, 36)) && (s.lineHeight === undefined || finite(s.lineHeight, 1, 2.5)) &&
    (s.align === undefined || ["left", "center", "right", "justify"].includes(s.align)) &&
    [s.bold, s.italic, s.underline].every((v) => v === undefined || typeof v === "boolean") &&
    (s.color === undefined || /^#[\da-f]{6}$/i.test(s.color));
};
export function isTextStyleRows(input: unknown): input is PdfEditorTextStyle[][] { return Array.isArray(input) && input.length <= 100 && input.every((row) => Array.isArray(row) && row.length <= 26 && row.every(isTextStyle)); }
export function isTextStyleGrid(input: unknown): input is PdfEditorTextStyle[][][] { return Array.isArray(input) && input.length <= 100 && input.every((row) => Array.isArray(row) && row.length <= 26 && row.every((children) => Array.isArray(children) && children.length <= 26 && children.every(isTextStyle))); }
export function isSubsubparagraphs(input: unknown, paragraphs: string[], subparagraphs: string[][]): input is string[][][] { return Array.isArray(input) && input.length <= paragraphs.length && input.every((row, i) => Array.isArray(row) && row.length <= (subparagraphs[i]?.length || 0) && row.every((children) => Array.isArray(children) && children.length <= 26 && children.every((s) => typeof s === "string" && s.length <= 4000))); }
/** Validate persisted/imported data before it can become an editor configuration. */
export function isTemplate(input: unknown): input is DocumentTemplate {
  if (!input || typeof input !== "object") return false;
  const t = input as DocumentTemplate;
  try {
    return knownKeys(t, ["version", "id", "name", "description", "sections", "rules", "layout", "formatting", "defaults", "images", "signatures", "apiFields"]) &&
      (t.images === undefined || isImages(t.images)) && (t.signatures === undefined || isSignatureSettingsList(t.signatures)) &&
      knownKeys(t.layout, ["size", "orientation", "marginTop", "marginRight", "marginBottom", "marginLeft", "paragraphGap"]) &&
      knownKeys(t.rules, ["minParagraphs", "maxParagraphs", "allowSubparagraphs", "allowSubsubparagraphs"]) && knownKeys(t.formatting, ["body", "title", "closing"]) &&
      knownKeys(t.defaults, ["text", "organization", "title", "reference", "recipient", "paragraphs", "paragraphSectionIds", "subparagraphs", "subsubparagraphs", "closing_text", "paragraphStyles", "subparagraphStyles", "subsubparagraphStyles", "numbered"]) && (t.defaults.text === undefined || isDocumentText(t.defaults.text)) &&
      t.version === 1 && typeof t.id === "string" && t.id.length <= 100 && typeof t.name === "string" && t.name.length <= 100 && typeof t.description === "string" && t.description.length <= 500 &&
      Array.isArray(t.sections) && t.sections.length <= 15 && new Set(t.sections.map(sectionId)).size === t.sections.length && new Set(t.sections.filter((s) => !isContentSection(s)).map((s) => s.kind)).size === t.sections.filter((s) => !isContentSection(s)).length &&
      t.sections.every((s) => knownKeys(s, ["id", "kind", "label", "required", "locked", "maxLength", "rules", "style", "numbered", "bindings"]) && (s.id === undefined || typeof s.id === "string" && /^[a-zA-Z0-9_-]{1,100}$/.test(s.id)) && (isContentSection(s) || s.rules === undefined && s.style === undefined && s.numbered === undefined) && (s.rules === undefined || isParagraphRules(s.rules)) && (s.style === undefined || isTextStyle(s.style)) && (s.numbered === undefined || typeof s.numbered === "boolean") && sectionCatalog.some((c) => c.kind === s.kind) && typeof s.label === "string" && s.label.length <= 80 && typeof s.required === "boolean" && typeof s.locked === "boolean" && finite(s.maxLength, 1, s.kind === "header" ? 160 : 4000) && Number.isInteger(s.maxLength)) &&
      ["A4", "Letter"].includes(t.layout.size) && ["portrait", "landscape"].includes(t.layout.orientation) &&
      [t.layout.marginTop, t.layout.marginBottom].every((n) => finite(n, 25, 50)) && [t.layout.marginLeft, t.layout.marginRight].every((n) => finite(n, 15, 50)) && finite(t.layout.paragraphGap, 4, 32) &&
      finite(t.rules.minParagraphs, 1, 100) && finite(t.rules.maxParagraphs, 1, 100) && Number.isInteger(t.rules.minParagraphs) && Number.isInteger(t.rules.maxParagraphs) && typeof t.rules.allowSubparagraphs === "boolean" && (t.rules.allowSubsubparagraphs === undefined || typeof t.rules.allowSubsubparagraphs === "boolean") &&
      [t.formatting.body, t.formatting.title, t.formatting.closing].every(isTextStyle) &&
      [t.defaults.organization, t.defaults.title, t.defaults.reference, t.defaults.recipient, t.defaults.closing_text].every((v) => typeof v === "string" && v.length <= 4000) &&
      t.defaults.organization.length <= 160 && t.defaults.reference.length <= 100 && t.defaults.recipient.length <= 500 &&
      Array.isArray(t.defaults.paragraphs) && t.defaults.paragraphs.length <= 100 && t.defaults.paragraphs.every((v) => typeof v === "string" && v.length <= 4000) &&
      Array.isArray(t.defaults.subparagraphs) && t.defaults.subparagraphs.length <= 100 && t.defaults.subparagraphs.every((row) => Array.isArray(row) && row.length <= 26 && row.every((v) => typeof v === "string" && v.length <= 4000)) &&
      (t.defaults.subsubparagraphs === undefined || isSubsubparagraphs(t.defaults.subsubparagraphs, t.defaults.paragraphs, t.defaults.subparagraphs)) &&
      (t.defaults.paragraphStyles === undefined || (Array.isArray(t.defaults.paragraphStyles) && t.defaults.paragraphStyles.length <= 100 && t.defaults.paragraphStyles.every(isTextStyle))) &&
      (t.defaults.subparagraphStyles === undefined || isTextStyleRows(t.defaults.subparagraphStyles)) && (t.defaults.numbered === undefined || typeof t.defaults.numbered === "boolean") &&
      (t.defaults.subsubparagraphStyles === undefined || isTextStyleGrid(t.defaults.subsubparagraphStyles)) &&
      templateErrors(t).length === 0;
  } catch { return false; }
}
