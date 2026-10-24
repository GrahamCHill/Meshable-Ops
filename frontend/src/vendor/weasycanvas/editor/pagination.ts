import { clauseDepth, flattenContent } from "./content";
export type ParagraphFragment = { index: number; subIndex?: number; subsubIndex?: number; start: number; end: number; text: string };
export type PdfEditorPage = { first: boolean; fragments: ParagraphFragment[]; closing: boolean };

/** Browser pagination is advisory; the generator remains authoritative for exported page breaks. */
export function paginateDocument(
  paragraphs: string[], capacity: number, introductionHeight: number, closingHeight: number,
  measureText: (text: string, child?: boolean, index?: number, subIndex?: number, subsubIndex?: number) => number, subparagraphs: string[][] = [], gap = 18, subsubparagraphs: string[][][] = [], beforeParagraphHeights: number[] = [],
  protectedTextRanges?: (text: string, index: number) => { start: number; end: number }[],
): PdfEditorPage[] {
  if (![capacity, introductionHeight, closingHeight, gap].every(Number.isFinite) || capacity <= 0 || gap < 0 || gap >= capacity || introductionHeight < 0 || closingHeight < 0) {
    throw new RangeError("Use a positive page capacity, non-negative heights, and a paragraph gap smaller than the capacity.");
  }
  const pages: PdfEditorPage[] = [{ first: true, fragments: [], closing: false }];
  let remaining = Math.max(0, capacity - introductionHeight);
  const nextPage = () => { pages.push({ first: false, fragments: [], closing: false }); remaining = capacity; };
  const heightOf = (text: string, child = false, index?: number, subIndex?: number, subsubIndex?: number) => {
    const height = measureText(text, child, index, subIndex, subsubIndex);
    if (!Number.isFinite(height) || height < 0) throw new RangeError("Text measurements must be finite and non-negative.");
    return height + gap;
  };
  const rows = flattenContent({ paragraphs, subparagraphs, subsubparagraphs });
  if (beforeParagraphHeights.some((height) => !Number.isFinite(height) || height < 0 || height > capacity)) throw new RangeError("Paragraph section headings must fit inside the page body.");
  const heights = rows.map((row) => heightOf(row.text, row.subIndex !== undefined, row.index, row.subIndex, row.subsubIndex));
  const totals = [0]; for (const height of heights) totals.push(totals[totals.length - 1] + height);
  const ends = rows.map(() => rows.length), stack: number[] = [];
  rows.forEach((row, i) => { while (stack.length && clauseDepth(rows[stack[stack.length - 1]]) >= clauseDepth(row)) ends[stack.pop()!] = i; stack.push(i); });
  for (const [rowIndex, { text, index, subIndex, subsubIndex }] of rows.entries()) {
    const protectedRanges = protectedTextRanges?.(text, index) || [];
    const cuts = Array.from({ length: text.length + 1 }, (_, i) => i).filter((i) => !protectedRanges.some((range) => i > range.start && i < range.end));
    const leading = subIndex === undefined ? beforeParagraphHeights[index] || 0 : 0;
    const measure = (value: string) => value === text ? heights[rowIndex] : heightOf(value, subIndex !== undefined, index, subIndex, subsubIndex);
    if (ends[rowIndex] > rowIndex + 1) {
      const group = totals[ends[rowIndex]] - totals[rowIndex];
      const together = group + leading <= capacity ? group + leading : heights[rowIndex] + heights[rowIndex + 1] + leading;
      if (together <= capacity && together > remaining) nextPage();
    }
    if (leading) {
      const firstLine = heightOf(text.slice(0, protectedRanges.find((range) => range.start === 0)?.end || 1), false, index);
      if (leading + firstLine > capacity) throw new RangeError("A paragraph section heading and its first line must fit inside the page body.");
      const together = leading + heights[rowIndex] <= capacity ? leading + heights[rowIndex] : leading + firstLine;
      if (together > remaining) nextPage();
      remaining -= leading;
    }
    let start = 0;
    do {
      const rest = text.slice(start);
      const height = measure(rest);
      if (height <= capacity && height > remaining && !(start === 0 && leading)) nextPage();
      if (height <= remaining) {
        pages[pages.length - 1].fragments.push({ index, subIndex, subsubIndex, start, end: text.length, text: rest });
        remaining -= height; break;
      }
      if (remaining < Math.min(80, capacity) && !(start === 0 && leading)) nextPage();
      const availableCuts = cuts.filter((cut) => cut > start);
      let low = 0, high = availableCuts.length - 1, fit = 0;
      while (low <= high) {
        const middle = Math.floor((low + high) / 2);
        const length = availableCuts[middle] - start;
        if (measure(rest.slice(0, length)) <= remaining) { fit = length; low = middle + 1; }
        else high = middle - 1;
      }
      if (!fit) {
        if (remaining === capacity) throw new RangeError(protectedRanges.length ? "An inline API value cannot fit inside a page. Shorten the value or increase the writing area." : "A line of text cannot fit inside the page body. Increase the page capacity.");
        nextPage(); continue;
      }
      const boundary = rest.slice(0, fit).search(/\s+\S*$/);
      if (boundary > fit / 2) fit = boundary + 1;
      const end = start + fit;
      pages[pages.length - 1].fragments.push({ index, subIndex, subsubIndex, start, end, text: text.slice(start, end) });
      start = end;
      if (start < text.length) nextPage();
    } while (start < text.length);
  }
  if (closingHeight && remaining < closingHeight) nextPage();
  pages[pages.length - 1].closing = true;
  return pages;
}

export function replaceParagraphFragment(original: string, fragment: ParagraphFragment, replacement: string): string {
  const gap = fragment.end < original.length && /\s$/.test(fragment.text) && !/\s$/.test(replacement) ? " " : "";
  return original.slice(0, fragment.start) + replacement + gap + original.slice(fragment.end);
}
