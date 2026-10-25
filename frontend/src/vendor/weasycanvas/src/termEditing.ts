import { insertParagraph } from "../editor/content";
import type { PdfEditorContent } from "../editor/types";

export function parseTerms(source: string, separator: "lines" | "paragraphs") {
  return source.replace(/\r\n?/g, "\n").split(separator === "lines" ? /\n/ : /\n[\t ]*\n+/).map((text) => text.trim()).filter(Boolean);
}

/** Insert atomically so a failed bulk paste never loses or partially adds wording. */
export function insertSectionTerms(content: PdfEditorContent, sectionId: string, texts: string[], limits: { maxParagraphs: number; maxTextLength: number }, afterIndex?: number): { content: PdfEditorContent; index?: number; error?: string } {
  const ids = content.paragraphSectionIds || content.paragraphs.map(() => sectionId);
  const indices = ids.flatMap((id, i) => id === sectionId ? [i] : []);
  if (!texts.length) return { content, error: "Enter some text to add." };
  if (indices.length + texts.length > limits.maxParagraphs || content.paragraphs.length + texts.length > 100) return { content, error: "These terms exceed the section or document limit. Add fewer terms or increase the section maximum in its properties." };
  if (texts.some((text) => text.length > limits.maxTextLength)) return { content, error: `Each term must be ${limits.maxTextLength} characters or fewer. Split the longer text before adding it.` };
  if (afterIndex !== undefined && !indices.includes(afterIndex)) return { content, error: "Choose a term in this section." };
  const index = afterIndex === undefined ? (indices.length ? indices[indices.length - 1] + 1 : content.paragraphs.length) : afterIndex + 1;
  let next: PdfEditorContent = { ...content, paragraphSectionIds: ids };
  texts.forEach((text, offset) => { next = insertParagraph(next, index + offset, text, sectionId); });
  return { content: next, index };
}
