import { useLayoutEffect, useRef, useState } from "react";
import { clauseDepth, clauseStyle, clauseText, flattenContent, moveClause, removeClause, setClauseText, updateSubparagraphs, updateSubsubparagraphs } from "../editor/content";
import { clausePath } from "../editor/structure";
import { clauseReference, romanNumeral } from "../editor/numbering";
import { InlineText } from "../editor/InlineText";
import { textStyle } from "../editor/formatting";
import type { PdfEditorContent, PdfEditorTextTarget } from "../editor/types";
import { sectionId, sectionIds, sectionRules } from "./paragraphSections";
import { insertSectionTerms, parseTerms } from "./termEditing";
import { TemplateTextField } from "./TemplateTextField";
import type { DocumentTemplate, TemplateSection } from "./templates";

export function TemplateTermsEditor({ template, section, onChange, presentation = "form", onSelect }: { template: DocumentTemplate; section: TemplateSection; onChange: (template: DocumentTemplate) => void; presentation?: "form" | "page"; onSelect?: () => void }) {
  const container = useRef<HTMLDivElement>(null), pendingFocus = useRef<PdfEditorTextTarget>();
  const [bulk, setBulk] = useState(""), [separator, setSeparator] = useState<"lines" | "paragraphs">("lines"), [error, setError] = useState("");
  const content = template.defaults, id = sectionId(section), ids = sectionIds(content, template), rules = sectionRules(template, section);
  const indices = ids.flatMap((value, i) => value === id ? [i] : []), terms = section.kind === "body", noun = terms ? "term" : "paragraph";
  const rows = flattenContent(content).filter((row) => ids[row.index] === id);
  const atLimit = indices.length >= rules.maxParagraphs || content.paragraphs.length >= 100;
  const parsed = parseTerms(bulk, separator);
  const page = presentation === "page";
  useLayoutEffect(() => {
    const target = pendingFocus.current;
    if (!target) return;
    const input = container.current?.querySelector<HTMLElement>(`[data-term-path="${clausePath(target)}"] textarea, [data-term-path="${clausePath(target)}"] [contenteditable="true"]`);
    if (input) { pendingFocus.current = undefined; input.focus({ preventScroll: true }); if (input instanceof HTMLTextAreaElement) input.setSelectionRange(input.value.length, input.value.length); input.scrollIntoView({ block: "nearest" }); }
  }, [content]);
  function commit(next: PdfEditorContent, focus?: PdfEditorTextTarget) {
    pendingFocus.current = focus;
    setError("");
    onChange({ ...template, defaults: { ...template.defaults, ...next, subparagraphs: next.subparagraphs || [] } });
  }
  function add(texts = [""], afterIndex?: number) {
    const result = insertSectionTerms(content, id, texts, { maxParagraphs: rules.maxParagraphs, maxTextLength: section.maxLength }, afterIndex);
    if (result.error) { setError(result.error); return false; }
    commit(result.content, { index: result.index! });
    return true;
  }
  function addChild(target: PdfEditorTextTarget) {
    const children = content.subparagraphs[target.index] || [];
    if (target.subIndex === undefined) {
      if (!terms || !rules.allowSubparagraphs || children.length >= 26) return;
      commit(updateSubparagraphs(content, target.index, [...children, ""]), { index: target.index, subIndex: children.length });
    } else {
      const grandchildren = content.subsubparagraphs?.[target.index]?.[target.subIndex] || [];
      if (!terms || !rules.allowSubparagraphs || rules.allowSubsubparagraphs === false || grandchildren.length >= 26) return;
      commit(updateSubsubparagraphs(content, target.index, target.subIndex, [...grandchildren, ""]), { index: target.index, subIndex: target.subIndex, subsubIndex: grandchildren.length });
    }
  }
  function move(target: PdfEditorTextTarget, direction: -1 | 1) {
    const focus = target.subsubIndex !== undefined ? { ...target, subsubIndex: target.subsubIndex + direction }
      : target.subIndex !== undefined ? { ...target, subIndex: target.subIndex + direction }
      : { index: indices[indices.indexOf(target.index) + direction] };
    commit(moveClause(content, target, direction), focus);
  }
  return <div ref={container} className={`template-terms-editor${page ? " template-terms-page" : ""}`} role="group" aria-label={`Edit ${section.label}`} onFocusCapture={onSelect} onClick={(event) => event.stopPropagation()}>
    {!page ? <><div className="template-terms-toolbar"><strong>{indices.length} {noun}{indices.length === 1 ? "" : "s"}</strong><button type="button" disabled={atLimit} onClick={() => add()}>+ Add {noun}</button></div>
    <p className="muted">Edit your wording below. Enter adds a line break; use Add {noun} for a separate {noun}.</p>
    {section.locked ? <p className="muted">You can edit these defaults here. Content is locked when documents are created.</p> : null}</> : null}
    {rows.map((row) => {
      const depth = clauseDepth(row), localIndex = indices.indexOf(row.index), number = clauseReference({ ...row, index: localIndex });
      const label = `${depth ? "Subterm" : terms ? "Term" : "Paragraph"} ${number}`;
      const siblings = depth === 2 ? content.subsubparagraphs?.[row.index]?.[row.subIndex!] || [] : depth === 1 ? content.subparagraphs[row.index] || [] : indices;
      const siblingIndex = row.subsubIndex ?? row.subIndex ?? localIndex;
      const childCount = depth === 1 ? content.subsubparagraphs?.[row.index]?.[row.subIndex!]?.length || 0 : content.subparagraphs[row.index]?.length || 0;
      const marker = depth === 2 ? `(${romanNumeral(row.subsubIndex! + 1)})` : depth === 1 ? `${String.fromCharCode(97 + row.subIndex!)}.` : (section.numbered ?? template.defaults.numbered) === false ? "•" : `${localIndex + 1}.`;
      return <div key={clausePath(row)} className="template-term" data-term-path={clausePath(row)} data-depth={depth} style={page ? { marginBottom: template.layout.paragraphGap } : undefined}>
        {page ? <div className={`template-render-term${terms ? " template-render-numbered" : ""}`} style={textStyle({ ...template.formatting.body, ...section.style, ...clauseStyle(content, row) })}>
          {terms ? <span className="template-render-marker" aria-hidden="true">{marker}</span> : null}
          <div className="template-render-field"><InlineText label={`${section.label} — ${label}`} sectionId={id} value={clauseText(content, row)} editable multiline maxLength={section.maxLength} placeholder={`Click to add ${noun} text`} onCommit={(text) => commit(setClauseText(content, row, text))} /></div>
        </div> : <TemplateTextField label={label} ariaLabel={`${section.label} — ${label}`} rows={3} value={clauseText(content, row)} maxLength={section.maxLength} fields={template.apiFields} onChange={(text) => commit(setClauseText(content, row, text))} />}
        <div className="template-term-actions">
          {depth === 0 ? <button type="button" disabled={atLimit} aria-label={`Add ${noun} after ${number}`} onClick={() => add([""], row.index)}>+ Add after</button> : null}
          {terms && depth < 2 && rules.allowSubparagraphs && (depth === 0 || rules.allowSubsubparagraphs !== false) ? <button type="button" disabled={childCount >= 26} aria-label={`Add subterm to ${number}`} onClick={() => addChild(row)}>+ Subterm</button> : null}
          {([-1, 1] as const).map((direction) => <button type="button" key={direction} aria-label={`Move ${label.toLowerCase()} ${direction === -1 ? "up" : "down"}`} disabled={siblingIndex + direction < 0 || siblingIndex + direction >= siblings.length} onClick={() => move(row, direction)}>{direction === -1 ? "↑ Up" : "↓ Down"}</button>)}
          <button type="button" disabled={depth === 0 && indices.length <= rules.minParagraphs} aria-label={`Remove ${label.toLowerCase()}`} title={depth === 0 && indices.length <= rules.minParagraphs ? `Keep at least ${rules.minParagraphs} ${noun}(s) in this section` : `Remove ${label.toLowerCase()} and its subterms`} onClick={() => commit(removeClause(content, row))}>Remove</button>
        </div>
      </div>;
    })}
    {page || indices.length > 1 ? <button type="button" className="full-width" disabled={atLimit} onClick={() => add()}>+ Add {noun}</button> : null}
    {atLimit ? <p className="muted">Section or document limit reached. The section maximum is {rules.maxParagraphs}; the document maximum is 100.</p> : null}
    {!page ? <details className="template-terms-bulk"><summary>Add multiple {noun}s</summary>
      <label>Separate {noun}s by<select aria-label={`Separate ${noun}s by`} value={separator} onChange={(event) => setSeparator(event.target.value as typeof separator)}><option value="lines">One per line</option><option value="paragraphs">A blank line between them</option></select></label>
      <label>Paste your {noun}s<textarea aria-label={`Paste ${noun}s`} rows={6} maxLength={400000} value={bulk} onChange={(event) => { setBulk(event.target.value); setError(""); }} /></label>
      <p className="muted">{separator === "lines" ? "Each non-empty line becomes a separate " + noun + "." : "Blank lines separate " + noun + "s. Line breaks within each one are kept."} Text is appended to this section.</p>
      <button type="button" disabled={!parsed.length || atLimit} onClick={() => { if (add(parsed)) setBulk(""); }}>Add {parsed.length || ""} {noun}{parsed.length === 1 ? "" : "s"}</button>
    </details> : null}
    {error ? <p className="notice error" role="alert">{error}</p> : null}
  </div>;
}
