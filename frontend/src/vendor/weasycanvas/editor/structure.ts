import { clauseDepth, clauseStyle, clauseText } from "./content";
import { inlineApiKeys } from "./inlineApi";
import type { PdfEditorContent, PdfEditorTextStyle, PdfEditorTextTarget } from "./types";

export type PdfEditorStructureLimits = {
  minParagraphs?: number; maxParagraphs?: number; maxTotalParagraphs?: number;
  maxSubparagraphs?: number; maxSubsubparagraphs?: number;
};
export type PdfEditorStructureResult = { content: PdfEditorContent; target?: PdfEditorTextTarget; error?: string };
type Clause = { text: string; style: PdfEditorTextStyle; children: Clause[]; section: string };
const path = (target: PdfEditorTextTarget) => [target.index, ...(target.subIndex === undefined ? [] : [target.subIndex]), ...(target.subsubIndex === undefined ? [] : [target.subsubIndex])];
export const clausePath = (target: PdfEditorTextTarget) => path(target).join(".");
const targetAt = (indices: number[]): PdfEditorTextTarget => ({ index: indices[0], ...(indices.length > 1 ? { subIndex: indices[1] } : {}), ...(indices.length > 2 ? { subsubIndex: indices[2] } : {}) });
function tree(content: PdfEditorContent): Clause[] {
  const make = (target: PdfEditorTextTarget, children: Clause[] = []): Clause => ({ text: clauseText(content, target), style: { ...clauseStyle(content, target) }, children, section: content.paragraphSectionIds?.[target.index] || "body" });
  return content.paragraphs.map((_, index) => make({ index }, (content.subparagraphs?.[index] || []).map((_, subIndex) => make({ index, subIndex }, (content.subsubparagraphs?.[index]?.[subIndex] || []).map((_, subsubIndex) => make({ index, subIndex, subsubIndex }))))));
}
function location(roots: Clause[], target: PdfEditorTextTarget) {
  let siblings = roots;
  const indices = path(target);
  for (const at of indices.slice(0, -1)) { if (!siblings[at]) return; siblings = siblings[at].children; }
  const index = indices[indices.length - 1], node = siblings[index];
  return node ? { siblings, index, node } : undefined;
}
function serialize(content: PdfEditorContent, roots: Clause[]): PdfEditorContent {
  return { ...content, paragraphs: roots.map(n => n.text), ...(content.paragraphSectionIds ? { paragraphSectionIds: roots.map(n => n.section) } : {}),
    paragraphStyles: roots.map(n => n.style), subparagraphs: roots.map(n => n.children.map(c => c.text)),
    subparagraphStyles: roots.map(n => n.children.map(c => c.style)), subsubparagraphs: roots.map(n => n.children.map(c => c.children.map(l => l.text))),
    subsubparagraphStyles: roots.map(n => n.children.map(c => c.children.map(l => l.style))) };
}
function find(roots: Clause[], node: Clause, prefix: number[] = []): number[] | undefined {
  for (let i = 0; i < roots.length; i++) { const at = [...prefix, i]; if (roots[i] === node) return at; const nested = find(roots[i].children, node, at); if (nested) return nested; }
}
const height = (node: Clause): number => node.children.length ? 1 + Math.max(...node.children.map(height)) : 0;
function valid(roots: Clause[], section: string, limits: PdfEditorStructureLimits) {
  const count = roots.filter(n => n.section === section).length;
  if (count < (limits.minParagraphs ?? 1)) return "Keep the template's minimum number of main clauses.";
  if (count > (limits.maxParagraphs ?? 100) || roots.length > (limits.maxTotalParagraphs ?? 100)) return "The template's main clause limit has been reached.";
  for (const root of roots.filter(n => n.section === section)) {
    if (root.children.length > Math.min(26, limits.maxSubparagraphs ?? 26)) return "The template's subclause limit has been reached.";
    if (root.children.some(n => n.children.length > Math.min(26, limits.maxSubsubparagraphs ?? 26))) return "The template's subsubclause limit has been reached.";
    if (height(root) > 2) return "This family cannot be nested further: only three clause levels are supported.";
  }
}
export function clauseHasApiFields(content: PdfEditorContent, target: PdfEditorTextTarget) {
  const source = [clauseText(content, target) || "", ...(target.subIndex === undefined ? content.subparagraphs?.[target.index] || [] : []), ...(target.subsubIndex !== undefined ? [] : target.subIndex === undefined ? content.subsubparagraphs?.[target.index]?.flat() || [] : content.subsubparagraphs?.[target.index]?.[target.subIndex] || [])];
  return source.some(text => inlineApiKeys(text).length > 0);
}
/** Change one level, carrying all descendants and their formatting. Never crosses sections. */
export function changeClauseLevel(content: PdfEditorContent, target: PdfEditorTextTarget, direction: "indent" | "outdent", limits: PdfEditorStructureLimits = {}): PdfEditorStructureResult {
  const roots = tree(content), source = location(roots, target), depth = clauseDepth(target);
  if (!source) return { content, error: "Select an existing clause." };
  if (direction === "indent") {
    const previous = source.siblings[source.index - 1];
    if (depth === 2 || !previous || previous.section !== source.node.section) return { content, error: depth === 2 ? "This clause is already at the deepest level." : "A preceding clause at the same level is needed to nest this family." };
    source.siblings.splice(source.index, 1); previous.children.push(source.node);
  } else {
    if (depth === 0) return { content, error: "This clause is already a main clause." };
    const parent = location(roots, targetAt(path(target).slice(0, -1)))!;
    source.siblings.splice(source.index, 1); parent.siblings.splice(parent.index + 1, 0, source.node);
  }
  const error = valid(roots, source.node.section, limits);
  return error ? { content, error } : { content: serialize(content, roots), target: targetAt(find(roots, source.node)!) };
}
/** Drop before/after a clause of the same level, optionally into a different parent. */
export function relocateClause(content: PdfEditorContent, from: PdfEditorTextTarget, to: PdfEditorTextTarget, placement: "before" | "after", limits: PdfEditorStructureLimits = {}): PdfEditorStructureResult {
  const roots = tree(content), source = location(roots, from), destination = location(roots, to);
  if (!source || !destination || clauseDepth(from) !== clauseDepth(to)) return { content, error: "Drop beside a clause at the same level. Use Tab or Ctrl+Tab to change levels." };
  if (source.node.section !== destination.node.section) return { content, error: "Clause families must stay in their template section." };
  if (source.node === destination.node) return { content };
  source.siblings.splice(source.index, 1);
  destination.siblings.splice(destination.siblings.indexOf(destination.node) + (placement === "after" ? 1 : 0), 0, source.node);
  const error = valid(roots, source.node.section, limits);
  return error ? { content, error } : { content: serialize(content, roots), target: targetAt(find(roots, source.node)!) };
}
