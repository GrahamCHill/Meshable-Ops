import { resolveApiField, type PdfEditorApiField, type PdfEditorApiValues } from "./apiFields";

export type InlineApiPart = { text: string; start: number; end: number; key?: string };
/** Markers stay in the stored source. Only their displayed values change on an API refresh. */
export function inlineApiParts(source: string): InlineApiPart[] {
  const parts: InlineApiPart[] = []; let at = 0;
  for (const match of source.matchAll(/\{\{api:([a-zA-Z][a-zA-Z0-9_.-]{0,119})\}\}/g)) {
    const start = match.index!;
    if (start > at) parts.push({ text: source.slice(at, start), start: at, end: start });
    at = start + match[0].length; parts.push({ text: match[0], key: match[1], start, end: at });
  }
  if (at < source.length || !parts.length) parts.push({ text: source.slice(at), start: at, end: source.length });
  return parts;
}
export const inlineApiKeys = (source: string) => inlineApiParts(source).flatMap((part) => part.key ? [part.key] : []);
export const sameInlineApiFields = (a: string, b: string) => JSON.stringify(inlineApiKeys(a)) === JSON.stringify(inlineApiKeys(b));
export function inlineApiValue(key: string, fields: PdfEditorApiField[] = [], values: PdfEditorApiValues = {}) {
  const field = fields.find((f) => f.key === key);
  return field ? resolveApiField(field, values).value : `[API: ${key}]`;
}
export const resolveInlineApiText = (source: string, fields: PdfEditorApiField[] = [], values: PdfEditorApiValues = {}) => inlineApiParts(source).map((part) => part.key ? inlineApiValue(part.key, fields, values) : part.text).join("");
