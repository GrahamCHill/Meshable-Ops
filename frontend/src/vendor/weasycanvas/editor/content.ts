import type { PdfEditorContent, PdfEditorTextTarget } from "./types";

export const clauseDepth = (target: PdfEditorTextTarget) => target.subsubIndex !== undefined ? 2 : target.subIndex !== undefined ? 1 : 0;
export function clauseText(content: PdfEditorContent, target: PdfEditorTextTarget): string {
  return target.subsubIndex !== undefined ? content.subsubparagraphs?.[target.index]?.[target.subIndex!]?.[target.subsubIndex] || "" : target.subIndex !== undefined ? content.subparagraphs?.[target.index]?.[target.subIndex] || "" : content.paragraphs[target.index];
}
export function clauseStyle(content: PdfEditorContent, target: PdfEditorTextTarget) {
  return target.subsubIndex !== undefined ? content.subsubparagraphStyles?.[target.index]?.[target.subIndex!]?.[target.subsubIndex] : target.subIndex !== undefined ? content.subparagraphStyles?.[target.index]?.[target.subIndex] : content.paragraphStyles?.[target.index];
}
export function flattenContent(content: PdfEditorContent): (PdfEditorTextTarget & { text: string })[] {
  return content.paragraphs.flatMap((text, index) => [
    { text, index }, ...(content.subparagraphs?.[index] || []).flatMap((text, subIndex) => [
      { text, index, subIndex }, ...(content.subsubparagraphs?.[index]?.[subIndex] || []).map((text, subsubIndex) => ({ text, index, subIndex, subsubIndex })),
    ]),
  ]);
}
function copyContent(content: PdfEditorContent) {
  const paragraphs = [...content.paragraphs];
  const subparagraphs = paragraphs.map((_, i) => [...(content.subparagraphs?.[i] || [])]);
  return { ...content, paragraphs, subparagraphs, ...(content.paragraphSectionIds ? { paragraphSectionIds: [...content.paragraphSectionIds] } : {}),
    subsubparagraphs: subparagraphs.map((row, i) => row.map((_, j) => [...(content.subsubparagraphs?.[i]?.[j] || [])])),
    paragraphStyles: paragraphs.map((_, i) => content.paragraphStyles?.[i] || {}),
    subparagraphStyles: subparagraphs.map((row, i) => row.map((_, j) => content.subparagraphStyles?.[i]?.[j] || {})),
    subsubparagraphStyles: subparagraphs.map((row, i) => row.map((_, j) => (content.subsubparagraphs?.[i]?.[j] || []).map((_, k) => content.subsubparagraphStyles?.[i]?.[j]?.[k] || {}))),
  };
}
export function setClauseText(content: PdfEditorContent, target: PdfEditorTextTarget, text: string): PdfEditorContent {
  const next = copyContent(content);
  if (target.subsubIndex !== undefined) next.subsubparagraphs[target.index][target.subIndex!][target.subsubIndex] = text;
  else if (target.subIndex !== undefined) next.subparagraphs[target.index][target.subIndex] = text;
  else next.paragraphs[target.index] = text;
  return next;
}
/** Reorder a whole clause family, including descendants and formatting. */
export function moveClause(content: PdfEditorContent, target: PdfEditorTextTarget, direction: -1 | 1): PdfEditorContent {
  const next = copyContent(content), { index, subIndex, subsubIndex } = target;
  if (subIndex === undefined && content.paragraphSectionIds && content.paragraphSectionIds[index] !== content.paragraphSectionIds[index + direction]) return content;
  const swap = <T,>(rows: T[], i: number) => { if (i >= 0 && i < rows.length && i + direction >= 0 && i + direction < rows.length) [rows[i], rows[i + direction]] = [rows[i + direction], rows[i]]; };
  if (subsubIndex !== undefined) { swap(next.subsubparagraphs[index][subIndex!], subsubIndex); swap(next.subsubparagraphStyles[index][subIndex!], subsubIndex); }
  else if (subIndex !== undefined) { swap(next.subparagraphs[index], subIndex); swap(next.subparagraphStyles[index], subIndex); swap(next.subsubparagraphs[index], subIndex); swap(next.subsubparagraphStyles[index], subIndex); }
  else { swap(next.paragraphs, index); swap(next.subparagraphs, index); swap(next.subsubparagraphs, index); swap(next.paragraphStyles, index); swap(next.subparagraphStyles, index); swap(next.subsubparagraphStyles, index); }
  return next;
}
export function removeClause(content: PdfEditorContent, target: PdfEditorTextTarget): PdfEditorContent {
  const next = copyContent(content), { index, subIndex, subsubIndex } = target;
  if (index < 0 || index >= next.paragraphs.length) return content;
  if (subsubIndex !== undefined) { next.subsubparagraphs[index][subIndex!].splice(subsubIndex, 1); next.subsubparagraphStyles[index][subIndex!].splice(subsubIndex, 1); }
  else if (subIndex !== undefined) { next.subparagraphs[index].splice(subIndex, 1); next.subparagraphStyles[index].splice(subIndex, 1); next.subsubparagraphs[index].splice(subIndex, 1); next.subsubparagraphStyles[index].splice(subIndex, 1); }
  else if (next.paragraphs.length > 1) { next.paragraphs.splice(index, 1); next.paragraphSectionIds?.splice(index, 1); next.subparagraphs.splice(index, 1); next.subsubparagraphs.splice(index, 1); next.paragraphStyles.splice(index, 1); next.subparagraphStyles.splice(index, 1); next.subsubparagraphStyles.splice(index, 1); }
  return next;
}
export function insertParagraph(content: PdfEditorContent, index: number, text = "New paragraph.", sectionId?: string): PdfEditorContent {
  const next = copyContent(content), at = Math.max(0, Math.min(index, next.paragraphs.length));
  next.paragraphs.splice(at, 0, text); next.subparagraphs.splice(at, 0, []); next.subsubparagraphs.splice(at, 0, []);
  next.paragraphStyles.splice(at, 0, {}); next.subparagraphStyles.splice(at, 0, []); next.subsubparagraphStyles.splice(at, 0, []);
  if (next.paragraphSectionIds) next.paragraphSectionIds.splice(at, 0, sectionId || next.paragraphSectionIds[Math.max(0, at - 1)] || "body");
  return next;
}
export function moveParagraph(content: PdfEditorContent, index: number, direction: -1 | 1): PdfEditorContent { return moveClause(content, { index }, direction); }
export function removeParagraph(content: PdfEditorContent, index: number): PdfEditorContent { return removeClause(content, { index }); }
export function updateSubparagraphs(content: PdfEditorContent, index: number, children: string[]): PdfEditorContent {
  if (index < 0 || index >= content.paragraphs.length) return content;
  const subparagraphs = content.paragraphs.map((_, i) => [...(content.subparagraphs?.[i] || [])]); subparagraphs[index] = [...children];
  return { ...content, subparagraphs };
}
export function updateSubsubparagraphs(content: PdfEditorContent, index: number, subIndex: number, children: string[]): PdfEditorContent {
  if (!content.subparagraphs?.[index] || subIndex < 0 || subIndex >= content.subparagraphs[index].length) return content;
  const subsubparagraphs = content.paragraphs.map((_, i) => (content.subparagraphs?.[i] || []).map((_, j) => [...(content.subsubparagraphs?.[i]?.[j] || [])])); subsubparagraphs[index][subIndex] = [...children];
  return { ...content, subsubparagraphs };
}
