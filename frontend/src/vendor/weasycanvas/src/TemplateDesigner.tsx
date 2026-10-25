import { useEffect, useMemo, useRef, useState, type DragEvent } from "react";
import { clone, createDocumentFromTemplate, newSection, pageDimensions, sectionCatalog, templateErrors, type DocumentTemplate, type SectionKind, type TemplateSection } from "./templates";
import { ApiFieldsProvider, clauseDepth, clauseReference, clauseStyle, flattenContent, textStyle } from "../editor/index";
import { TemplateTypography } from "./TemplateTypography";
import { ImageRegion } from "./ImageRegion";
import { SignatureGroup } from "./SignatureGroup";
import { defaultSignature, fitImage, isImages, pageText, readImage, signatureFields, signaturePosition, signatureMarker, type DocumentImage, type ImageSection, type SignatureSettings } from "./media";
import { footerText, headingText } from "./documentText";
import { bodySections, isContentSection, sectionId, sectionIds, sectionRules, sectionContent, orderedContent, normalizeParagraphTemplate } from "./paragraphSections";
import { insertParagraph } from "../editor/index";
import { isApiValues, type PdfEditorApiValues } from "../editor/index";
import { ApiBindingsPanel, newApiField } from "./ApiBindingsPanel";
import { InlineApiPanel } from "./InlineApiPanel";
import { TemplateTextField } from "./TemplateTextField";
import { TemplateTermsEditor } from "./TemplateTermsEditor";
import { RenderedTemplateSection } from "./RenderedTemplateSection";
import { resolvedInlineDocument } from "./inlineFields";
import type { PdfEditorImageStorage } from "../editor/index";

export function TemplateDesigner({ template: templateValue, onChange, onSave, onUse, onExport, onImport, apiValues = {}, onApiValuesChange, imageStorage }: {
  template: DocumentTemplate; onChange: (value: DocumentTemplate) => void; onSave: () => void;
  onUse: () => void; onExport: () => void; onImport: () => void;
  apiValues?: PdfEditorApiValues; onApiValuesChange?: (values: PdfEditorApiValues) => void;
  imageStorage?: PdfEditorImageStorage;
}) {
  const template = useMemo(() => normalizeParagraphTemplate(templateValue), [templateValue]);
  const [selected, setSelected] = useState<string>(() => sectionId(bodySections(template)[0]));
  const [dropTarget, setDropTarget] = useState<string | null>(null);
  const [mediaError, setMediaError] = useState("");
  const [storeImages, setStoreImages] = useState(false);
  const [imageUploading, setImageUploading] = useState(false);
  const uploadAbort = useRef<AbortController | null>(null);
  useEffect(() => () => uploadAbort.current?.abort(), []);
  const [showRuler, setRuler] = useState(true);
  const [pageEditing, setPageEditing] = useState(true);
  const [apiJson, setApiJson] = useState(() => JSON.stringify(apiValues, null, 2));
  const [apiJsonError, setApiJsonError] = useState("");
  const previewDocument = resolvedInlineDocument(createDocumentFromTemplate(template, apiValues));
  const uploadInput = useRef<HTMLInputElement>(null);
  const [selectedSignature, setSelectedSignature] = useState(0);
  const signatures = signatureFields(template.defaults.closing_text, template.signatures);
  const signingIndex = Math.min(selectedSignature, Math.max(0, signatures.length - 1));
  const signing = signatures[signingIndex] || defaultSignature;
  const section = template.sections.find((s) => sectionId(s) === selected);
  const errors = templateErrors(template);
  const repeatingText = pageText(previewDocument.organization, previewDocument.reference, template.sections, previewDocument.text?.footer);
  const latestUploadState = useRef({ template, onChange, repeatingText }); latestUploadState.current = { template, onChange, repeatingText };
  const dimensions = pageDimensions(template.layout);
  const previewWidth = template.layout.orientation === "landscape" ? 900 : 650;
  const rules = section ? sectionRules(template, section) : template.rules;
  const changeSection = (patch: Partial<TemplateSection>) => onChange({ ...template, sections: template.sections.map((s) => sectionId(s) === selected ? { ...s, ...patch } : s) });
  function place(kind: SectionKind, before?: string, existingId?: string) {
    if (!sectionCatalog.some((s) => s.kind === kind) || existingId && existingId === before) return;
    const existing = existingId ? template.sections.find((s) => sectionId(s) === existingId) : !isContentSection({ kind }) ? template.sections.find((s) => s.kind === kind) : undefined;
    if (!existing && isContentSection({ kind }) && (bodySections(template).length >= 10 || template.defaults.paragraphs.length >= 100)) return;
    const added = existing || { ...newSection(kind), ...(kind === "body" ? { numbered: true, rules: { ...template.rules, allowSubparagraphs: true, allowSubsubparagraphs: true } } : {}), ...(isContentSection({ kind }) ? { id: crypto.randomUUID(), label: `${kind === "text" ? "Paragraphs" : "Terms"} ${template.sections.filter((s) => s.kind === kind).length + 1}` } : {}) };
    const flow = template.sections.filter((s) => sectionId(s) !== sectionId(added) && s.kind !== "header" && s.kind !== "footer");
    const index = before ? flow.findIndex((s) => sectionId(s) === before) : isContentSection({ kind }) ? flow.findIndex((s) => s.kind === "closing") : -1;
    if (kind !== "header" && kind !== "footer") flow.splice(index < 0 ? flow.length : index, 0, added);
    const header = kind === "header" ? added : template.sections.find((s) => s.kind === "header");
    const footer = kind === "footer" ? added : template.sections.find((s) => s.kind === "footer");
    const sections = [...(header ? [header] : []), ...flow, ...(footer ? [footer] : [])];
    const next = { ...template, sections };
    const content = !existing && isContentSection({ kind }) ? insertParagraph(orderedContent(template.defaults, template), template.defaults.paragraphs.length, "", sectionId(added)) : template.defaults;
    onChange({ ...next, defaults: orderedContent(content, next) as DocumentTemplate["defaults"] });
    setSelected(sectionId(added)); setDropTarget(null);
  }
  function drop(event: DragEvent, before?: string) {
    event.preventDefault(); event.stopPropagation();
    const value = event.dataTransfer.getData("text/plain");
    if (value === "add:api") { addApiBlock(before); setDropTarget(null); return; }
    if (value.startsWith("move:")) { const id = value.slice(5), section = template.sections.find((s) => sectionId(s) === id); if (section) place(section.kind, before, id); }
    else if (value.startsWith("add:")) place(value.slice(4) as SectionKind, before);
  }
  function reorder(id: string, direction: -1 | 1) {
    const sections = [...template.sections]; const index = sections.findIndex((s) => sectionId(s) === id); const target = index + direction;
    if (!sections[target] || ["header", "footer"].includes(sections[target].kind) || ["header", "footer"].includes(sections[index].kind)) return;
    [sections[index], sections[target]] = [sections[target], sections[index]];
    const next = { ...template, sections }; onChange({ ...next, defaults: orderedContent(template.defaults, next) });
  }
  function removeSection(current: TemplateSection) {
    if (isContentSection(current) && bodySections(template).length === 1) return;
    const id = sectionId(current), ids = sectionIds(template.defaults, template), keep = ids.flatMap((value, i) => !isContentSection(current) || value !== id ? [i] : []);
    const defaults = { ...template.defaults, paragraphs: keep.map((i) => template.defaults.paragraphs[i]), paragraphSectionIds: keep.map((i) => ids[i]),
      subparagraphs: keep.map((i) => template.defaults.subparagraphs[i] || []), subsubparagraphs: keep.map((i) => template.defaults.subsubparagraphs?.[i] || []),
      paragraphStyles: keep.map((i) => template.defaults.paragraphStyles?.[i] || {}), subparagraphStyles: keep.map((i) => template.defaults.subparagraphStyles?.[i] || []), subsubparagraphStyles: keep.map((i) => template.defaults.subsubparagraphStyles?.[i] || []) };
    if (defaults.text?.sectionHeadings) defaults.text = { ...defaults.text, sectionHeadings: Object.fromEntries(Object.entries(defaults.text.sectionHeadings).filter(([key]) => key !== id)) };
    onChange({ ...template, defaults, sections: template.sections.filter((s) => sectionId(s) !== id), images: template.images?.filter((i) => isContentSection(current) ? i.section !== current.kind || (i.sectionId || sectionId(bodySections(template)[0])) !== id : i.section !== current.kind) });
    setSelected(sectionId(bodySections(template).find((s) => sectionId(s) !== id) || bodySections(template)[0]));
  }
  function bodyPreview(current: TemplateSection) {
    const content = sectionContent(previewDocument, template, sectionId(current)), currentRules = sectionRules(template, current);
    const clauses = flattenContent(content).filter((r) => r.subIndex === undefined || currentRules.allowSubparagraphs && (r.subsubIndex === undefined || currentRules.allowSubsubparagraphs !== false));
    return <div className="mock-body">{clauses.slice(0, 12).map((row) => <p key={clauseReference(row)} style={{ ...textStyle({ ...template.formatting.body, ...current.style, ...clauseStyle(content, row) }), marginLeft: clauseDepth(row) * 18 }}>{current.kind === "text" ? null : <>{(current.numbered ?? template.defaults.numbered) === false ? "•" : `${clauseReference(row)}.`} &nbsp;</>}{row.text.slice(0, 250)}{row.text.length > 250 ? "…" : ""}</p>)}{clauses.length > 12 ? <p className="muted">{clauses.length - 12} more clauses in this section</p> : null}</div>;
  }
  const defaults = (key: keyof DocumentTemplate["defaults"], value: string) => onChange({ ...template, defaults: { ...template.defaults, [key]: value } });
  function updateImages(images: DocumentImage[]) {
    if (!isImages(images)) { setMediaError("Use at most 8 images and 1.5 MB of embedded image data."); return; }
    const sections = [...template.sections];
    for (const image of images) if (!sections.some((s) => s.kind === image.section)) { if (image.section === "header") sections.unshift(newSection("header")); else { const footer = sections.findIndex((s) => s.kind === "footer"); sections.splice(footer < 0 ? sections.length : footer, 0, newSection(image.section)); } }
    onChange({ ...template, images, sections }); setMediaError("");
  }
  async function upload(file?: File) {
    if (!file || !section || (template.images?.length || 0) >= 8) return;
    uploadAbort.current?.abort(); const controller = new AbortController(); uploadAbort.current = controller;
    const targetId = sectionId(section), templateId = template.id; setImageUploading(true); setMediaError("");
    try {
      const result = await readImage(file), name = file.name.slice(0, 160);
      if (controller.signal.aborted) return;
      const source = storeImages && imageStorage ? await imageStorage.upload({ name, dataUrl: result.dataUrl }, controller.signal) : { dataUrl: result.dataUrl };
      if (controller.signal.aborted) return;
      const current = latestUploadState.current, target = current.template.sections.find(s => sectionId(s) === targetId);
      if (current.template.id !== templateId) throw new Error("The template changed during upload. Add the image to the new template again.");
      if (!target || (current.template.images?.length || 0) >= 8) throw new Error("The image's section was removed or its image limit was reached during upload.");
      const image = fitImage({ id: crypto.randomUUID(), name, ...source, section: target.kind as ImageSection, ...(isContentSection(target) ? { sectionId: targetId } : {}), widthMm: 50, heightMm: 50 * result.height / result.width, xMm: 0 }, current.template.layout, current.template.images || [], current.repeatingText);
      const images = [...(current.template.images || []), image];
      if (!isImages(images)) throw new Error("Use valid image sources, at most 8 images and 1.5 MB of embedded image data.");
      current.onChange({ ...current.template, images });
    } catch (error) { if (!controller.signal.aborted) setMediaError(error instanceof Error ? error.message : "Could not add image."); }
    finally { if (uploadAbort.current === controller && !controller.signal.aborted) setImageUploading(false); }
  }
  const changeSigning = (patch: Partial<SignatureSettings>) => onChange({ ...template, signatures: signatures.map((s, i) => i === signingIndex ? { ...s, ...patch } : s) });
  function addApiBlock(before?: string) {
    if (bodySections(template).length >= 10 || template.defaults.paragraphs.length >= 100 || (template.apiFields?.length || 0) >= 50) return;
    const field = newApiField(template), added: TemplateSection = { ...newSection("text"), id: crypto.randomUUID(), label: field.label, bindings: { text: field.key }, rules: { minParagraphs: 1, maxParagraphs: 1, allowSubparagraphs: false, allowSubsubparagraphs: false } };
    const sections = [...template.sections];
    let at = sections.findIndex((s) => before ? sectionId(s) === before : s.kind === "closing");
    if (at < 0) at = sections.findIndex((s) => s.kind === "footer");
    if (at < 0) at = sections.length;
    if (at === 0 && sections[0]?.kind === "header") at = 1;
    sections.splice(at, 0, added);
    const next = { ...template, sections, apiFields: [...(template.apiFields || []), field] };
    onChange({ ...next, defaults: { ...template.defaults, ...orderedContent(insertParagraph(template.defaults, template.defaults.paragraphs.length, "", sectionId(added)), next) } }); setSelected(sectionId(added));
  }
  return <ApiFieldsProvider fields={template.apiFields} values={apiValues}><div className="designer">
    <div className="designer-toolbar">
      <div><h2>Template designer</h2><p>Choose sections, wording, data fields, and page layout.</p></div>
      <div className="button-row"><button onClick={onImport}>Import template</button><button onClick={onExport} disabled={!!errors.length || imageUploading}>Export JSON</button><button onClick={onSave} disabled={!!errors.length || imageUploading}>Save template</button><button className="primary" onClick={onUse} disabled={!!errors.length || imageUploading}>Save & create document</button></div>
    </div>
    {errors.length ? <div className="notice error" role="alert">{errors.join(" ")}</div> : null}
    {mediaError ? <div className="notice error" role="alert">{mediaError}</div> : null}
    <input ref={uploadInput} type="file" hidden aria-label="Upload template image" accept="image/png,image/jpeg,image/webp" onChange={(event) => { void upload(event.target.files?.[0]); event.target.value = ""; }} />
    <div className="designer-layout">
      <aside className="designer-palette panel">
        <span className="eyebrow">YOUR TEMPLATE</span>
        <label>Template name<input aria-label="Template name" maxLength={100} value={template.name} onChange={(e) => onChange({ ...template, name: e.target.value })} /></label>
        <label>Description<textarea maxLength={500} rows={3} value={template.description} onChange={(e) => onChange({ ...template, description: e.target.value })} /></label>
        <div className="panel-divider" /><h3>Document sections</h3><p className="muted">Click to add a section. Add individual terms inside a Terms section.</p>
        {sectionCatalog.map((item) => <button className="palette-item" key={item.kind} disabled={isContentSection(item) && (bodySections(template).length >= 10 || template.defaults.paragraphs.length >= 100)} draggable onDragStart={(e) => e.dataTransfer.setData("text/plain", `add:${item.kind}`)} onClick={() => !isContentSection(item) && template.sections.some((s) => s.kind === item.kind) ? setSelected(sectionId(template.sections.find((s) => s.kind === item.kind)!)) : place(item.kind)}>
          <span className="section-icon">{item.icon}</span><span><strong>{item.label}</strong><small>{item.description}</small></span><span className="palette-plus">{!isContentSection(item) && template.sections.some((s) => s.kind === item.kind) ? "✓" : "+"}</span>
        </button>)}
        <button className="api-palette-item" aria-label="Add data field block" draggable onDragStart={(e) => e.dataTransfer.setData("text/plain", "add:api")} disabled={bodySections(template).length >= 10 || template.defaults.paragraphs.length >= 100 || (template.apiFields?.length || 0) >= 50} onClick={() => addApiBlock()}>+ data field block<small>Read-only text supplied by your application</small></button>
        <p className="designer-tip">Header and footer repeat on every page. Other sections flow in the order you choose.</p>
        <InlineApiPanel template={template} onChange={onChange} />
        {template.apiFields?.length ? <details className="api-preview-data" open><summary>Preview field values</summary><p className="muted">Example values for this draft. Saved templates keep bindings and fallbacks, not these values.</p><textarea aria-label="Preview field values JSON" rows={7} spellCheck={false} value={apiJson} onChange={(e) => { setApiJson(e.target.value); try { const values: unknown = JSON.parse(e.target.value); if (!isApiValues(values)) throw new Error("Use a JSON object with up to 50 text, number, boolean or null values."); onApiValuesChange?.(values); setApiJsonError(""); } catch (error) { setApiJsonError(error instanceof Error ? error.message : "Invalid JSON."); } }} />{apiJsonError ? <p role="alert" className="api-value-error">{apiJsonError}</p> : null}</details> : null}
      </aside>
      <div className="designer-stage">
        <div className="template-page-modes" role="group" aria-label="Template editing view"><button type="button" aria-pressed={pageEditing} onClick={() => setPageEditing(true)}>Edit page</button><button type="button" aria-pressed={!pageEditing} onClick={() => setPageEditing(false)}>Manage terms</button></div>
        <p className="template-page-hint">{pageEditing ? "Click text on the page to edit it. Enter adds a line break. Data fields show preview values; change their placement in Manage terms." : "Select a section to add, reorder, or paste multiple terms."}</p>
        <div className="designer-page-caption" style={{ maxWidth: previewWidth }}><span>{template.layout.size} · {template.layout.orientation}</span><label className="check-row"><input type="checkbox" checked={showRuler} onChange={(e) => setRuler(e.target.checked)} />Page ruler</label><span>{template.sections.length} sections</span></div>
        {showRuler ? <div className="designer-ruler" aria-label="Template page ruler in centimetres" style={{ maxWidth: previewWidth }}>{Array.from({ length: Math.floor(dimensions.width / 10) + 1 }, (_, i) => <span key={i} style={{ left: `${i * 10 / dimensions.width * 100}%` }}>{i}</span>)}</div> : null}
        <div className={`designer-page ${pageEditing ? "template-render-page" : ""} ${dropTarget === "end" ? "drop-active" : ""}`} style={{ maxWidth: previewWidth, minHeight: template.layout.orientation === "landscape" ? 480 : 740 }} onDragOver={(e) => { e.preventDefault(); setDropTarget("end"); }} onDragLeave={() => setDropTarget(null)} onDrop={(e) => drop(e)}>
          {template.sections.map((s, index) => <div key={sectionId(s)} data-section={s.kind} data-section-id={sectionId(s)} className={`designer-section ${selected === sectionId(s) ? "selected" : ""} ${dropTarget === sectionId(s) ? "drop-active" : ""}`}
            onDragOver={(e) => { e.preventDefault(); e.stopPropagation(); setDropTarget(sectionId(s)); }} onDrop={(e) => drop(e, sectionId(s))}>
            <div className="designer-section-select" onClick={() => setSelected(sectionId(s))}>
              <button type="button" className="designer-section-heading" draggable onDragStart={(e) => { e.stopPropagation(); e.dataTransfer.setData("text/plain", `move:${sectionId(s)}`); e.dataTransfer.effectAllowed = "move"; }} aria-label={`Configure ${s.label}`} aria-pressed={selected === sectionId(s)}><span>{s.label}</span><small>{s.locked ? "Locked" : s.required ? "Required" : "Optional"}{s.kind === "header" || s.kind === "footer" ? " · Repeating" : ""}</small></button>
              <ImageRegion images={template.images || []} section={s.kind} sectionId={isContentSection(s) ? sectionId(s) : undefined} sections={template.sections} layout={template.layout} text={repeatingText} editable onChange={updateImages} />
              {Object.keys(s.bindings || {}).length ? <span className="api-source-tag">API · {Object.values(s.bindings || {}).join(", ")}</span> : null}
              {pageEditing ? <RenderedTemplateSection template={template} section={s} preview={previewDocument} onChange={onChange} onSelect={() => setSelected(sectionId(s))} /> : null}
              {!pageEditing ? <>
              {s.bindings?.heading && headingText(previewDocument, s).trim() ? <h3>{headingText(previewDocument, s)}</h3> : null}
              {s.kind === "header" ? <div className="mock-header">{previewDocument.organization || "Your organisation"}</div> : null}
              {s.kind === "title" ? <div className="mock-title" style={textStyle({ ...template.formatting.body, ...template.formatting.title })}>{previewDocument.title || "Document title"}</div> : null}
              {s.kind === "details" ? <div className="mock-details">{template.defaults.text?.referenceLabel ?? "Reference:"} {previewDocument.reference}<br />{template.defaults.text?.recipientLabel ?? "Prepared for:"} {previewDocument.recipient}</div> : null}
              {s.kind === "closing" ? <div className="mock-closing" style={textStyle({ ...template.formatting.body, ...template.formatting.closing })}>{(signatureMarker(previewDocument.closing_text) ? previewDocument.closing_text.replace(signatureMarker(previewDocument.closing_text)![0], "") : previewDocument.closing_text) || "Your closing text"}{signatures.length ? <SignatureGroup signatures={signatures} /> : null}</div> : null}
              {s.kind === "footer" ? <div className="mock-footer">{footerText(previewDocument)}</div> : null}
              </> : null}
              {isContentSection(s) ? (pageEditing || selected === sectionId(s)) && !s.bindings?.text ? <TemplateTermsEditor key={sectionId(s)} template={template} section={s} onChange={onChange} presentation={pageEditing ? "page" : "form"} onSelect={() => setSelected(sectionId(s))} /> : <>{bodyPreview(s)}{!s.bindings?.text ? <button type="button" onClick={() => setSelected(sectionId(s))}>Edit {s.kind === "body" ? "terms" : "paragraphs"}</button> : null}</> : null}
            </div>
            <div className="designer-section-actions">
              <button aria-label={`Move ${s.label} up`} disabled={index === 0 || s.kind === "header" || s.kind === "footer" || template.sections[index - 1]?.kind === "header"} onClick={() => reorder(sectionId(s), -1)}>↑</button>
              <button aria-label={`Move ${s.label} down`} disabled={index === template.sections.length - 1 || s.kind === "header" || s.kind === "footer" || template.sections[index + 1]?.kind === "footer"} onClick={() => reorder(sectionId(s), 1)}>↓</button>
              <button aria-label={`Remove ${s.label}`} disabled={isContentSection(s) && bodySections(template).length === 1} onClick={() => removeSection(s)}>×</button>
            </div>
          </div>)}
          <div className="designer-drop-zone" onDragOver={(e) => e.preventDefault()} onDrop={(e) => drop(e)}>+ Drop a section here</div>
        </div>
        <p className="muted designer-caption">Changes in both views update the same draft. Save the template when finished; generate a PDF to verify the final layout.</p>
      </div>
      <aside className="designer-inspector panel">
        <span className="eyebrow">SECTION PROPERTIES</span><h3>{section?.label || "Select a section"}</h3>
        {section ? <>
          <label>Section label<input maxLength={80} value={section.label} onChange={(e) => changeSection({ label: e.target.value })} /></label>
          {section.kind !== "footer" ? <>
            <label className="check-row"><input type="checkbox" checked={section.required} onChange={(e) => changeSection({ required: e.target.checked })} />Required text</label>
            <label className="check-row"><input type="checkbox" checked={section.locked} onChange={(e) => changeSection({ locked: e.target.checked })} />Lock content in editor</label>
            <label>Characters per field<input aria-label="Characters per field" type="number" min={1} max={section.kind === "header" ? 160 : 4000} value={section.maxLength} onChange={(e) => changeSection({ maxLength: Math.max(1, Math.min(section.kind === "header" ? 160 : 4000, Number(e.target.value))) })} /></label>
          </> : section.kind === "footer" ? <><TemplateTextField label="Default footer" rows={3} fields={template.apiFields} maxLength={section.maxLength} value={footerText(template.defaults)} onChange={(value) => onChange({ ...template, defaults: { ...template.defaults, text: { ...template.defaults.text, footer: value } } })} /><button onClick={() => { const { footer: _footer, ...text } = template.defaults.text || {}; onChange({ ...template, defaults: { ...template.defaults, text } }); }}>Use organisation and reference</button></> : null}
          {imageStorage ? <label>Image storage<select aria-label="Image storage" value={storeImages ? "storage" : "embedded"} disabled={imageUploading} onChange={event => setStoreImages(event.target.value === "storage")}><option value="embedded">Embed in template</option><option value="storage">Connected blob / S3 storage</option></select></label> : null}
          <button className="full-width" disabled={imageUploading || (template.images?.length || 0) >= 8} onClick={() => uploadInput.current?.click()}>{imageUploading ? "Uploading image…" : `+ Add image to ${section.label.toLowerCase()}`}</button>
          <ApiBindingsPanel template={template} section={section} onChange={onChange} />
          {section.kind === "closing" ? <label className="check-row"><input type="checkbox" checked={!!signatureMarker(template.defaults.closing_text)} onChange={(event) => { const marker = signatureMarker(template.defaults.closing_text); onChange({ ...template, signatures: event.target.checked ? [{ ...defaultSignature }] : [], defaults: { ...template.defaults, closing_text: event.target.checked ? `${template.defaults.closing_text.trimEnd()}\n\n{{signature}}` : marker ? (template.defaults.closing_text.slice(0, marker.index) + template.defaults.closing_text.slice(marker.index! + marker[0].length)).trimEnd() : template.defaults.closing_text } }); setSelectedSignature(0); }} />Signature line in closing</label> : null}
          {section.kind === "closing" && signatureMarker(template.defaults.closing_text) ? <>
            <div className="panel-divider" /><h3>Signature fields</h3>
            <label>Number of signatures<select aria-label="Number of signatures" value={signatures.length} onChange={(e) => { const count = Number(e.target.value); onChange({ ...template, signatures: Array.from({ length: count }, (_, i) => signatures[i] || { ...defaultSignature }) }); setSelectedSignature(Math.min(signingIndex, count - 1)); }}>{[1, 2, 3, 4].map((count) => <option key={count} value={count}>{count}</option>)}</select></label>
            {signatures.length > 1 ? <><p className="muted">Two per row: left, right, then the next row.</p><label>Signature to configure<select aria-label="Signature to configure" value={signingIndex} onChange={(e) => setSelectedSignature(Number(e.target.value))}>{signatures.map((_, i) => <option key={i} value={i}>{i + 1} · {signaturePosition(i)}</option>)}</select></label></> : null}
            <label>Signature field ID<input aria-label="Signature field ID" maxLength={100} value={signing.id || ""} onChange={(event) => changeSigning({ id: event.target.value })} /></label>
            <p className="muted">Use this unique ID to read or update this signing field through your application's API.</p>
            <label>Line label<textarea aria-label="Signature line label" rows={2} maxLength={100} value={signing.label} onChange={(e) => changeSigning({ label: e.target.value })} /></label>
            <label>Default signer name<textarea rows={2} maxLength={160} value={signing.signerName} onChange={(e) => changeSigning({ signerName: e.target.value })} /></label>
            <label>Line width (mm)<input type="number" aria-label="Signature line width" min={40} max={120} value={signing.widthMm} onChange={(e) => changeSigning({ widthMm: Math.max(40, Math.min(120, Number(e.target.value))) })} /></label>
            {signatures.length === 1 ? <label>Alignment<select value={signing.align} aria-label="Signature alignment" onChange={(e) => changeSigning({ align: e.target.value as SignatureSettings["align"] })}><option value="left">Left</option><option value="center">Centre</option><option value="right">Right</option></select></label> : null}
            <label className="check-row"><input type="checkbox" checked={signing.required} onChange={(e) => changeSigning({ required: e.target.checked })} />Require signature before export</label>
            <p className="muted">Templates keep the line and its rules. Each new document starts unsigned.</p>
          </> : null}
          {isContentSection(section) ? <><div className="panel-divider" /><h3>{section.kind === "text" ? "General text rules" : "Terms rules"}</h3>
            <TemplateTextField label="Section heading (optional)" ariaLabel="Default section heading" fields={template.apiFields} rows={2} maxLength={80} value={headingText(template.defaults, section)} onChange={(value) => onChange({ ...template, defaults: { ...template.defaults, text: { ...template.defaults.text, sectionHeadings: { ...template.defaults.text?.sectionHeadings, [sectionId(section)]: value } } } })} />
            <p className="muted">Leave blank for no heading. The section label is only used to organise the editor.</p>
            <div className="field-pair">{(["minParagraphs", "maxParagraphs"] as const).map((key) => <label key={key}>{key === "minParagraphs" ? "Minimum" : "Maximum"}<input aria-label={key === "minParagraphs" ? "Minimum paragraphs" : "Maximum paragraphs"} type="number" min={1} max={100} value={rules[key]} onChange={(e) => changeSection({ rules: { ...rules, [key]: Math.max(1, Math.min(100, Number(e.target.value))) } })} /></label>)}</div>
            {section.kind === "body" ? <><label className="check-row"><input type="checkbox" checked={rules.allowSubparagraphs} onChange={(e) => changeSection({ rules: { ...rules, allowSubparagraphs: e.target.checked } })} />Allow lettered subparagraphs</label>
            <label className="check-row"><input type="checkbox" disabled={!rules.allowSubparagraphs} checked={rules.allowSubsubparagraphs !== false} onChange={(e) => changeSection({ rules: { ...rules, allowSubsubparagraphs: e.target.checked } })} />Allow Roman numeral subsubclauses</label></> : <p className="muted">General text has no numbering or nested clauses. Add a Terms section for clauses.</p>}
            {!section.bindings?.text ? <p className="muted">Edit all {section.kind === "body" ? "terms" : "paragraphs"} directly in the selected section. Use Add {section.kind === "body" ? "term" : "paragraph"} to extend it, or Add multiple to paste a list.</p> : null}
          </> : null}
          {section.kind === "title" || section.kind === "header" || section.kind === "closing" ? <TemplateTextField label="Default text" fields={template.apiFields} maxLength={section.maxLength} value={template.defaults[section.kind === "title" ? "title" : section.kind === "header" ? "organization" : "closing_text"]} onChange={(value) => defaults(section.kind === "title" ? "title" : section.kind === "header" ? "organization" : "closing_text", value)} /> : null}
          {section.kind === "details" ? <><TemplateTextField label="Default reference" rows={3} fields={template.apiFields} maxLength={Math.min(100, section.maxLength)} value={template.defaults.reference} onChange={(value) => defaults("reference", value)} /><TemplateTextField label="Default recipient" rows={4} fields={template.apiFields} maxLength={Math.min(500, section.maxLength)} value={template.defaults.recipient} onChange={(value) => defaults("recipient", value)} /></> : null}
          {section.kind !== "footer" ? <p className="muted">Press Enter in a text field to add a line break.</p> : null}
        </> : null}
        <TemplateTypography key={selected} template={template} onChange={onChange} initialTarget={isContentSection(section!) && bodySections(template).length > 1 ? `section:${selected}` : section?.kind === "title" || section?.kind === "closing" ? section.kind : "body"} />
        <div className="panel-divider" /><h3>Page setup</h3>
        <label>Paper size<select aria-label="Paper size" value={template.layout.size} onChange={(e) => onChange({ ...template, layout: { ...template.layout, size: e.target.value as "A4" | "Letter" } })}><option>A4</option><option>Letter</option></select></label>
        <label>Orientation<select aria-label="Page orientation" value={template.layout.orientation} onChange={(e) => onChange({ ...template, layout: { ...template.layout, orientation: e.target.value as "portrait" | "landscape" } })}><option value="portrait">Portrait</option><option value="landscape">Landscape</option></select></label>
        <div className="field-pair">{(["marginTop", "marginBottom", "marginLeft", "marginRight"] as const).map((key) => <label key={key}>{key.replace("margin", "")} margin (mm)<input aria-label={`${key.replace("margin", "")} margin`} type="number" min={key === "marginTop" || key === "marginBottom" ? 25 : 15} max={50} value={template.layout[key]} onChange={(e) => onChange({ ...template, layout: { ...template.layout, [key]: Math.max(key === "marginTop" || key === "marginBottom" ? 25 : 15, Math.min(50, Number(e.target.value))) } })} /></label>)}</div>
        <label>Space after paragraph<select aria-label="Paragraph spacing" value={template.layout.paragraphGap} onChange={(e) => onChange({ ...template, layout: { ...template.layout, paragraphGap: Number(e.target.value) } })}>{[4, 8, 12, 18, 24, 32].map((size) => <option key={size} value={size}>{size} px</option>)}</select></label>
        {section?.kind !== "text" ? <label className="check-row"><input type="checkbox" aria-label="Numbered paragraphs" checked={(section && isContentSection(section) ? section.numbered ?? template.defaults.numbered : template.defaults.numbered) !== false} onChange={(e) => section && isContentSection(section) ? changeSection({ numbered: e.target.checked }) : onChange({ ...template, defaults: { ...template.defaults, numbered: e.target.checked } })} />Numbered paragraphs</label> : null}
        <button className="full-width" onClick={() => onChange({ ...clone(template), id: crypto.randomUUID(), name: `${template.name.slice(0, 90)} copy` })}>Duplicate template</button>
      </aside>
    </div>
  </div></ApiFieldsProvider>;
}
