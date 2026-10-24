/** Document-owned copy; changing text never changes a saved template. */
export type DocumentText = {
  referenceLabel?: string;
  recipientLabel?: string;
  footer?: string;
  sectionHeadings?: Record<string, string>;
};

export const footerText = (value: { organization: string; reference: string; text?: DocumentText }) => value.text?.footer ?? `${value.organization} · ${value.reference}`;
export const headingText = (value: { text?: DocumentText }, section: { id?: string; kind: string; label: string }) => {
  const headings = value.text?.sectionHeadings, id = section.id || section.kind;
  return headings && Object.prototype.hasOwnProperty.call(headings, id) ? headings[id] : section.label;
};
export function isDocumentText(input: unknown): input is DocumentText {
  if (!input || typeof input !== "object" || Array.isArray(input)) return false;
  const text = input as DocumentText;
  return Object.keys(text).every((key) => ["referenceLabel", "recipientLabel", "footer", "sectionHeadings"].includes(key)) &&
    [text.referenceLabel, text.recipientLabel].every((s) => s === undefined || typeof s === "string" && s.length <= 80) &&
    (text.footer === undefined || typeof text.footer === "string" && text.footer.length <= 4000) &&
    (text.sectionHeadings === undefined || !!text.sectionHeadings && typeof text.sectionHeadings === "object" && !Array.isArray(text.sectionHeadings) && Object.keys(text.sectionHeadings).length <= 10 && Object.entries(text.sectionHeadings).every(([id, s]) => /^[a-zA-Z0-9_-]{1,100}$/.test(id) && typeof s === "string" && s.length <= 80));
}
export const extraDocumentText = (text?: DocumentText) => [text?.referenceLabel || "", text?.recipientLabel || "", text?.footer || "", ...Object.values(text?.sectionHeadings || {})];
