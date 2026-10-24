"use client";

import { Fragment, useEffect, useLayoutEffect, useMemo, useRef, useState, type CSSProperties, type DragEvent, type KeyboardEvent } from "react";
import { InlineText } from "./InlineText";
import { textStyle } from "./formatting";
import { ZoomBar } from "./ZoomBar";
import { ApiFieldsProvider } from "./ApiFieldsContext";
import { EditPolicyProvider } from "./EditPolicyContext";
import { inlineApiParts, resolveInlineApiText } from "./inlineApi";
import { changeClauseLevel, clauseHasApiFields, clausePath, relocateClause, type PdfEditorStructureResult } from "./structure";
import { clauseDepth, clauseStyle, clauseText, flattenContent, insertParagraph, moveClause, removeClause, setClauseText, updateSubparagraphs, updateSubsubparagraphs } from "./content";
import { clauseReference, romanNumeral } from "./numbering";
import { paginateDocument, replaceParagraphFragment, type PdfEditorPage } from "./pagination";
import type { PdfEditorContent, PdfEditorContext, PdfEditorParagraphSection, PdfEditorProps, PdfEditorTextTarget } from "./types";

const emptyPages: PdfEditorPage[] = [];

export function PdfEditor<TDocument>({
  document: value, onChange, onStructureChange, adapter, editable = true, className = "", pageLayout,
  labels, maxParagraphs = 100, maxSubparagraphs = 26, maxSubsubparagraphs = 26, maxTextLength = 4000, initialZoom = 0.85,
  renderHeader, renderFooter, renderIntroduction, renderClosing, renderDetails, renderToolbarActions,
  renderPdf, filename = "document.pdf", previewTitle = "Generated PDF",
  showRuler = false, showToolbar = true, minParagraphs = 1, numbered = true, paragraphEditable = true, onActiveTextChange, bodyStyle,
  paragraphSections, activeParagraphSectionId, renderBeforeParagraph,
  overlayPlacement = "container",
  apiFields, apiValues, editPolicy,
}: PdfEditorProps<TDocument>) {
  const content = useMemo(() => adapter.getContent(value), [adapter, value]);
  const layout = useMemo(() => ({ widthMm: 210, heightMm: 297, marginTopMm: 20, marginRightMm: 20,
    marginBottomMm: 20, marginLeftMm: 20, fontFamily: 'Arial, "Liberation Sans", Helvetica, sans-serif',
    fontSizePx: 14.2, lineHeight: 1.34, paragraphGapPx: 18, ...pageLayout }), [pageLayout]);
  const [zoom, setZoom] = useState(initialZoom);
  const [pagination, setPagination] = useState<{ content: PdfEditorContent; pages: PdfEditorPage[] } | null>(null);
  const pages = pagination?.content === content ? pagination.pages : emptyPages;
  const [detailsOpen, setDetailsOpen] = useState(false);
  const [layoutError, setLayoutError] = useState<string | null>(null);
  const [renderError, setRenderError] = useState<string | null>(null);
  const [rendering, setRendering] = useState(false);
  const [preview, setPreview] = useState<{ document: TDocument; url: string } | null>(null);
  const request = useRef<AbortController | null>(null);
  const capacity = useRef<HTMLDivElement>(null);
  const introduction = useRef<HTMLDivElement>(null);
  const textMeasure = useRef<HTMLDivElement>(null);
  const closing = useRef<HTMLDivElement>(null);
  const beforeMeasure = useRef<HTMLDivElement>(null);
  const [focusedSectionId, setFocusedSectionId] = useState<string | undefined>();
  const [structureNotice, setStructureNotice] = useState("");
  const dragged = useRef<PdfEditorTextTarget | null>(null);
  const [dropHint, setDropHint] = useState<{ path: string; placement: "before" | "after" } | null>(null);
  const pendingFocus = useRef<{ target: PdfEditorTextTarget; start?: number; end?: number } | null>(null);
  const sheets = useRef<HTMLDivElement>(null);
  const paragraphLabel = labels?.paragraph || "Paragraph";
  const childLabel = labels?.subparagraph || "Subparagraph";
  const grandchildLabel = labels?.subsubparagraph || "Subsubclause";
  const commit = (next: PdfEditorContent, structure = true) => (structure && onStructureChange ? onStructureChange : onChange)(adapter.withContent(value, next));
  const childrenAt = (index: number) => [...(content.subparagraphs?.[index] || [])];
  const inferredIds = [...new Set(content.paragraphSectionIds?.length ? content.paragraphSectionIds : ["body"])];
  const sections: PdfEditorParagraphSection[] = paragraphSections?.length ? paragraphSections : inferredIds.map((id) => ({ id, label: inferredIds.length > 1 ? id : paragraphLabel }));
  const indicesFor = (id: string) => content.paragraphs.flatMap((_, i) => !content.paragraphSectionIds || content.paragraphSectionIds[i] === id ? [i] : []);
  const sectionAt = (index: number) => {
    const configuration = sections.find((s) => s.id === content.paragraphSectionIds?.[index]) || sections[0];
    return { minParagraphs, maxParagraphs, maxSubparagraphs, maxSubsubparagraphs, maxTextLength, editable: paragraphEditable, numbered, ...configuration,
      ...(configuration.presentation === "text" ? { maxSubparagraphs: 0, maxSubsubparagraphs: 0, numbered: false } : {}), ...(configuration.apiFieldKey ? { editable: false } : {}) };
  };
  const editing = editable && !rendering;
  const canEditField = (id?: string) => editing && (!editPolicy || id !== undefined && !!editPolicy.fields?.includes(id));
  const canEditSection = (id: string) => {
    const section = sections.find((section) => section.id === id);
    return editing && paragraphEditable && !!section && section.editable !== false && !section.apiFieldKey && (!editPolicy || !!editPolicy.sections?.includes(id));
  };
  const canChangeStructure = (id: string) => canEditSection(id) && (!editPolicy || editPolicy.structure === true);
  const context: PdfEditorContext<TDocument> = { document: value, onChange, editable: editing, canEditField, canEditSection, canChangeStructure };
  const deny = () => false;
  const measurementContext: PdfEditorContext<TDocument> = { ...context, editable: false, canEditField: deny, canEditSection: deny, canChangeStructure: deny };
  const selectedSection = sections.find((s) => s.id === (activeParagraphSectionId || focusedSectionId)) || sections[0];
  const selectedIndices = indicesFor(selectedSection.id);
  const selectedRules = sectionAt(selectedIndices[0] ?? 0);
  const addParagraph = () => {
    const index = selectedIndices.length ? selectedIndices[selectedIndices.length - 1] + 1 : content.paragraphs.length;
    pendingFocus.current = { target: { index } };
    commit(insertParagraph(content, index, labels?.newParagraph || "New paragraph.", selectedSection.id));
  };
  const limitsAt = (index: number) => ({ ...sectionAt(index), maxTotalParagraphs: maxParagraphs });
  function applyStructure(result: PdfEditorStructureResult, selection?: { start?: number; end?: number }) {
    setStructureNotice(result.error || "");
    if (result.error || result.content === content) return false;
    if (result.target) { pendingFocus.current = { target: result.target, ...selection }; onActiveTextChange?.(result.target); }
    commit(result.content); return true;
  }
  function changeLevel(event: KeyboardEvent<HTMLDivElement>, target: PdfEditorTextTarget) {
    if (event.key !== "Tab" || event.altKey || event.metaKey || event.nativeEvent.isComposing || !(event.target instanceof HTMLElement) || !event.target.matches('textarea, [contenteditable="true"]')) return;
    const section = sectionAt(target.index);
    if (!canChangeStructure(section.id) || section.presentation === "text") return;
    const result = changeClauseLevel(content, target, event.ctrlKey || event.shiftKey ? "outdent" : "indent", limitsAt(target.index));
    const field = event.target instanceof HTMLTextAreaElement ? event.target : null;
    if (applyStructure(result, field ? { start: field.selectionStart, end: field.selectionEnd } : undefined) || event.ctrlKey) event.preventDefault();
  }
  function dragOver(event: DragEvent<HTMLDivElement>, target: PdfEditorTextTarget) {
    if (!dragged.current) return;
    event.preventDefault();
    const from = dragged.current;
    if (clauseDepth(from) !== clauseDepth(target) || sectionAt(from.index).id !== sectionAt(target.index).id || !canChangeStructure(sectionAt(target.index).id)) { event.dataTransfer.dropEffect = "none"; setDropHint(null); return; }
    event.dataTransfer.dropEffect = "move";
    const bounds = event.currentTarget.getBoundingClientRect();
    setDropHint({ path: clausePath(target), placement: event.clientY < bounds.y + bounds.height / 2 ? "before" : "after" });
  }
  function dropClause(event: DragEvent<HTMLDivElement>, target: PdfEditorTextTarget) {
    const from = dragged.current; if (!from) return;
    event.preventDefault(); event.stopPropagation(); dragged.current = null; setDropHint(null);
    if (!canChangeStructure(sectionAt(from.index).id) || !canChangeStructure(sectionAt(target.index).id)) return;
    const bounds = event.currentTarget.getBoundingClientRect();
    applyStructure(relocateClause(content, from, target, event.clientY < bounds.y + bounds.height / 2 ? "before" : "after", limitsAt(from.index)));
  }

  useEffect(() => {
    if (!pendingFocus.current) return;
    const frame = requestAnimationFrame(() => {
      const pending = pendingFocus.current; if (!pending) return;
      const field = sheets.current?.querySelector<HTMLElement>(`[data-clause-path="${clausePath(pending.target)}"] textarea, [data-clause-path="${clausePath(pending.target)}"] [contenteditable="true"]`);
      if (!field) return;
      pendingFocus.current = null; field.focus({ preventScroll: true });
      if (field instanceof HTMLTextAreaElement && pending.start !== undefined) field.setSelectionRange(pending.start, pending.end ?? pending.start);
      field.scrollIntoView({ block: "nearest", inline: "nearest" });
    });
    return () => cancelAnimationFrame(frame);
  }, [content, pages]);

  useLayoutEffect(() => {
    let active = true;
    const recalculate = () => {
      if (!active || !capacity.current || !introduction.current || !textMeasure.current || !closing.current) return;
      try {
        if (!sections.length || new Set(sections.map((s) => s.id)).size !== sections.length || content.paragraphSectionIds && (content.paragraphSectionIds.length !== content.paragraphs.length || content.paragraphSectionIds.some((id) => !sections.some((s) => s.id === id))) || paragraphSections && paragraphSections.length > 1 && !content.paragraphSectionIds) throw new RangeError("Supply unique paragraph section IDs and one matching section ID per paragraph.");
        if (content.paragraphs.some((_, i) => sectionAt(i).presentation === "text" && ((content.subparagraphs?.[i]?.length || 0) > 0 || (content.subsubparagraphs?.[i]?.flat().length || 0) > 0))) throw new RangeError("General text cannot contain nested clauses. Use a Terms section.");
        const height = capacity.current.clientHeight;
        const firstHeight = introduction.current.getBoundingClientRect().height;
        const lastHeight = closing.current.getBoundingClientRect().height;
        if (firstHeight > height || lastHeight > height) throw new RangeError("The introduction or closing is taller than the page body. Adjust the template or page margins.");
        const measure = (text: string, child = false, index = 0, subIndex = 0, subsubIndex?: number) => {
          const parent = textMeasure.current!.parentElement!;
          parent.classList.toggle("pdf-editor-child", child);
          parent.classList.toggle("pdf-editor-grandchild", subsubIndex !== undefined);
          parent.classList.toggle("pdf-editor-text-paragraph", sectionAt(index).presentation === "text");
          parent.removeAttribute("style");
          Object.assign(parent.style, textStyle({ ...bodyStyle, ...sectionAt(index).style, ...clauseStyle(content, { index, subIndex: child ? subIndex : undefined, subsubIndex }) }));
          textMeasure.current!.textContent = (sectionAt(index).apiFieldKey ? text : resolveInlineApiText(text, apiFields, apiValues)) || " ";
          return textMeasure.current!.getBoundingClientRect().height;
        };
        const beforeHeights = content.paragraphs.map((_, i) => beforeMeasure.current?.children[i]?.getBoundingClientRect().height || 0);
        setPagination({ content, pages: paginateDocument(content.paragraphs, height, firstHeight, lastHeight, measure, content.subparagraphs, layout.paragraphGapPx, content.subsubparagraphs, beforeHeights, (text, index) => sectionAt(index).apiFieldKey ? [] : inlineApiParts(text).filter((part) => part.key)) });
        setLayoutError(null);
      } catch (error) { setLayoutError(error instanceof Error ? error.message : "Could not paginate this document."); }
    };
    recalculate();
    if (globalThis.document.fonts) void globalThis.document.fonts.ready.then(recalculate);
    const observer = typeof ResizeObserver === "undefined" ? null : new ResizeObserver(recalculate);
    for (const element of [capacity.current, introduction.current, closing.current, beforeMeasure.current]) if (element) observer?.observe(element);
    return () => { active = false; observer?.disconnect(); };
  }, [content, layout, value, renderIntroduction, renderClosing, bodyStyle, paragraphSections, renderBeforeParagraph, apiFields, apiValues]);

  useEffect(() => () => { request.current?.abort(); }, [value, renderPdf]);
  useEffect(() => { dragged.current = null; setDropHint(null); }, [content]);
  useEffect(() => () => { if (preview) URL.revokeObjectURL(preview.url); }, [preview]);

  async function exportPdf() {
    if (!renderPdf) return;
    request.current?.abort();
    const controller = new AbortController();
    request.current = controller;
    setRendering(true); setRenderError(null);
    try {
      const blob = await renderPdf(value, controller.signal);
      if (controller.signal.aborted) return;
      if (!blob.size || blob.type.split(";")[0].toLowerCase() !== "application/pdf") throw new Error("The generator must return a non-empty application/pdf blob.");
      setPreview({ document: value, url: URL.createObjectURL(blob) });
    } catch (error) {
      if (!controller.signal.aborted) setRenderError(error instanceof Error ? error.message : "The PDF could not be generated.");
    } finally { if (request.current === controller) setRendering(false); }
  }

  const style = {
    "--pdf-width": `${layout.widthMm}mm`, "--pdf-height": `${layout.heightMm}mm`,
    "--pdf-top": `${layout.marginTopMm}mm`, "--pdf-right": `${layout.marginRightMm}mm`,
    "--pdf-bottom": `${layout.marginBottomMm}mm`, "--pdf-left": `${layout.marginLeftMm}mm`,
    "--pdf-font": layout.fontFamily, "--pdf-font-size": `${layout.fontSizePx}px`,
    "--pdf-line-height": layout.lineHeight, "--pdf-paragraph-gap": `${layout.paragraphGapPx}px`,
  } as CSSProperties;
  const visiblePages: PdfEditorPage[] = pages.length ? pages : [{ first: true, closing: true, fragments: flattenContent(content).map((row) => ({ ...row, start: 0, end: row.text.length })) }];

  return <ApiFieldsProvider fields={apiFields} values={apiValues}><EditPolicyProvider value={context}><div className={`pdf-editor ${className}`} data-overlay-placement={overlayPlacement} style={style}>
    {showToolbar ? <div className="pdf-editor-tools">
      <div><strong>{visiblePages.length} {visiblePages.length === 1 ? "page" : "pages"}</strong>
        <span className="pdf-editor-help">{labels?.help ?? "Tab: nest clause · Ctrl+Tab or Shift+Tab: promote · Drag the grip to move a clause family."}</span></div>
      <div className="pdf-editor-tool-actions">
        {context.editable && renderDetails ? <button type="button" onClick={() => setDetailsOpen(!detailsOpen)}>{detailsOpen ? "Hide details" : labels?.details || "Document details"}</button> : null}
        {canChangeStructure(selectedSection.id) ? <button type="button" disabled={selectedIndices.length >= selectedRules.maxParagraphs || content.paragraphs.length >= maxParagraphs} onClick={addParagraph}>+ Add {paragraphLabel.toLowerCase()}{sections.length > 1 ? ` to ${selectedSection.label}` : ""}</button> : null}
        {renderToolbarActions?.(context)}
        {renderPdf ? <button type="button" disabled={rendering || !!layoutError} onClick={() => void exportPdf()}>{rendering ? "Generating..." : "Preview / export PDF"}</button> : null}
      </div>
    </div> : null}
    {layoutError || renderError ? <p className="pdf-editor-error" role="alert">{layoutError || renderError}</p> : null}
    {structureNotice ? <p className="pdf-editor-structure-notice" role="status">{structureNotice}</p> : null}
    {detailsOpen && context.editable && renderDetails ? <div className="pdf-editor-details">{renderDetails(context)}</div> : null}
    <div ref={sheets} className="pdf-editor-workspace">
    <div className="pdf-editor-sheets" style={{ zoom }}>
      {showRuler ? <div className="pdf-editor-ruler" aria-label="Page ruler in centimetres" style={{ paddingLeft: `${layout.marginLeftMm}mm`, paddingRight: `${layout.marginRightMm}mm` }}><div>{Array.from({ length: Math.floor((layout.widthMm - layout.marginLeftMm - layout.marginRightMm) / 10) + 1 }, (_, i) => <span key={i} style={{ left: `${i * 10}mm` }}>{i}</span>)}</div></div> : null}
      {visiblePages.map((page, pageIndex) => <section key={pageIndex} className="pdf-editor-paper" aria-label={`${labels?.page || "Document"} page ${pageIndex + 1}`}>
        {renderHeader?.(context)}
        <div className="pdf-editor-page-body">
          {page.first ? renderIntroduction?.(context) : null}
          {page.fragments.map((fragment) => {
            const section = sectionAt(fragment.index), sectionIndices = indicesFor(section.id), localIndex = sectionIndices.indexOf(fragment.index);
            const depth = clauseDepth(fragment), number = clauseReference({ ...fragment, index: localIndex });
            const prefix = sections.length > 1 ? `${section.label} — ` : "";
            const label = depth === 2 ? grandchildLabel : depth === 1 ? childLabel : paragraphLabel;
            const siblings = depth === 2 ? content.subsubparagraphs?.[fragment.index]?.[fragment.subIndex!] || [] : depth === 1 ? childrenAt(fragment.index) : content.paragraphs;
            const siblingIndex = fragment.subsubIndex ?? fragment.subIndex ?? localIndex;
            const grandchildren = content.subsubparagraphs?.[fragment.index]?.[fragment.subIndex!] || [];
            const marker = depth === 2 ? `(${romanNumeral(fragment.subsubIndex! + 1)})` : depth === 1 ? `${String.fromCharCode(97 + fragment.subIndex!)}.` : section.numbered ? `${localIndex + 1}.` : "•";
            return <Fragment key={`${fragment.index}-${fragment.subIndex ?? "parent"}-${fragment.subsubIndex ?? "child"}-${fragment.start}`}>
              {depth === 0 && fragment.start === 0 ? <div className="pdf-editor-before-paragraph">{renderBeforeParagraph?.(context, fragment.index)}</div> : null}
              <div data-paragraph-section={section.id} data-clause-path={clausePath(fragment)} data-drop-placement={dropHint?.path === clausePath(fragment) ? dropHint.placement : undefined} onKeyDown={(event) => changeLevel(event, fragment)} onDragOver={(event) => dragOver(event, fragment)} onDrop={(event) => dropClause(event, fragment)} className={`pdf-editor-paragraph${section.presentation === "text" ? " pdf-editor-text-paragraph" : ""}${depth ? " pdf-editor-child" : ""}${depth === 2 ? " pdf-editor-grandchild" : ""}`} style={textStyle({ ...bodyStyle, ...section.style, ...clauseStyle(content, fragment) })}>
              {canChangeStructure(section.id) && fragment.start === 0 ? <button type="button" className="pdf-editor-clause-grip" draggable aria-label={`${prefix}Drag ${label.toLowerCase()} ${number}`} title="Drag to move this clause and its subclauses" onDragStart={(event) => { dragged.current = { index: fragment.index, subIndex: fragment.subIndex, subsubIndex: fragment.subsubIndex }; event.dataTransfer.setData("application/x-weasycanvas-clause", clausePath(fragment)); event.dataTransfer.effectAllowed = "move"; }} onDragEnd={() => { dragged.current = null; setDropHint(null); }}>⠿</button> : null}
              {section.presentation !== "text" ? <span className="pdf-editor-number">{fragment.start === 0 ? marker : ""}</span> : null}
              <InlineText sectionId={section.id} sourceKey={section.apiFieldKey} value={fragment.text} label={`${prefix}${label} ${number}${fragment.start ? " continued" : ""}`} editable={canEditSection(section.id)} multiline maxLength={Math.max(0, section.maxTextLength - (clauseText(content, fragment).length - fragment.text.length))}
                onFocus={() => { setFocusedSectionId(section.id); onActiveTextChange?.({ index: fragment.index, subIndex: fragment.subIndex, subsubIndex: fragment.subsubIndex }); }}
                onCommit={(text) => commit(setClauseText(content, fragment, replaceParagraphFragment(clauseText(content, fragment), fragment, text)), false)} />
              {canChangeStructure(section.id) && fragment.start === 0 ? <div className="pdf-editor-paragraph-actions">
                {depth === 0 && section.maxSubparagraphs > 0 ? <button type="button" title="Add a lettered subparagraph" aria-label={`${prefix}Add ${childLabel.toLowerCase()} to ${paragraphLabel.toLowerCase()} ${number}`} disabled={childrenAt(fragment.index).length >= Math.min(26, section.maxSubparagraphs)} onClick={() => commit(updateSubparagraphs(content, fragment.index, [...childrenAt(fragment.index), labels?.newSubparagraph || "New subparagraph."]))}>+a</button> : null}
                {depth === 1 && section.maxSubparagraphs > 0 && section.maxSubsubparagraphs > 0 ? <button type="button" title="Add a Roman numeral subsubclause" aria-label={`${prefix}Add ${grandchildLabel.toLowerCase()} to ${childLabel.toLowerCase()} ${number}`} disabled={grandchildren.length >= Math.min(26, section.maxSubsubparagraphs)} onClick={() => commit(updateSubsubparagraphs(content, fragment.index, fragment.subIndex!, [...grandchildren, labels?.newSubsubparagraph || "New subsubclause."]))}>+i</button> : null}
                {([-1, 1] as const).map((direction) => <button key={direction} type="button" aria-label={`${prefix}Move ${label.toLowerCase()} ${number} ${direction < 0 ? "up" : "down"}`} disabled={siblingIndex + direction < 0 || siblingIndex + direction >= (depth === 0 ? sectionIndices.length : siblings.length)} onClick={() => commit(moveClause(content, fragment, direction))}>{direction < 0 ? "↑" : "↓"}</button>)}
                <button type="button" aria-label={`${prefix}Delete ${label.toLowerCase()} ${number}`} title={clauseHasApiFields(content, fragment) ? "Contains a protected API field" : depth === 0 && sectionIndices.length <= section.minParagraphs ? "Keep the template's minimum number of paragraphs" : `Delete ${label.toLowerCase()} ${number} and its subclauses`} disabled={clauseHasApiFields(content, fragment) || depth === 0 && sectionIndices.length <= section.minParagraphs} onClick={() => commit(removeClause(content, fragment))}>×</button>
              </div> : null}
            </div></Fragment>;
          })}
          {page.closing ? renderClosing?.(context) : null}
        </div>
        {renderFooter?.(context)}
        <span className="pdf-editor-page-count">{pageIndex + 1} / {visiblePages.length}</span>
      </section>)}
    </div>
    </div>
    <ZoomBar placement={overlayPlacement} zoom={zoom} onChange={setZoom} pages={visiblePages.length} onFit={() => setZoom(Math.max(.4, Math.min(1.5, ((sheets.current?.clientWidth || 900) - 64) / (layout.widthMm * 96 / 25.4))))} />
    <EditPolicyProvider value={measurementContext}><div className="pdf-editor-measure" aria-hidden="true" ref={element => element?.setAttribute('inert', '')}>
      <div ref={capacity} className="pdf-editor-capacity" />
      <div ref={introduction}>{renderIntroduction?.(measurementContext)}</div>
      <div className="pdf-editor-paragraph"><span className="pdf-editor-number" /><div ref={textMeasure} className="pdf-editor-text" /></div>
      <div ref={closing}>{renderClosing?.(measurementContext)}</div>
      <div ref={beforeMeasure}>{content.paragraphs.map((_, index) => <div className="pdf-editor-before-paragraph" key={index}>{renderBeforeParagraph?.(measurementContext, index)}</div>)}</div>
    </div></EditPolicyProvider>
    {preview?.document === value ? <div className="pdf-editor-preview" role="dialog" aria-modal={overlayPlacement === "viewport"} aria-label={previewTitle} onKeyDown={(event) => { if (event.key === "Escape") setPreview(null); }}>
      <div className="pdf-editor-tools"><strong>{previewTitle}</strong><div className="pdf-editor-tool-actions">
        <a href={preview.url} download={filename}>Download PDF</a><button type="button" autoFocus onClick={() => setPreview(null)}>Return to editing</button>
      </div></div>
      <iframe title={previewTitle} src={`${preview.url}#view=Fit&toolbar=1&navpanes=0`} />
    </div> : null}
  </div></EditPolicyProvider></ApiFieldsProvider>;
}
