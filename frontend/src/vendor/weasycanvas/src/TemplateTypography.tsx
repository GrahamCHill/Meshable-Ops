import { useState } from "react";
import { clauseStyle, clauseReference, flattenContent, setClauseText, type PdfEditorTextStyle } from "../editor/index";
import { TemplateTextField } from "./TemplateTextField";
import { clone, defaultTemplate, type DocumentTemplate } from "./templates";
import { bodySections, sectionId } from "./paragraphSections";

export function TemplateTypography({ template, onChange, initialTarget = "body" }: {
  template: DocumentTemplate; onChange: (template: DocumentTemplate) => void; initialTarget?: string;
}) {
  const [target, setTarget] = useState<string>(initialTarget);
  const rows = flattenContent(template.defaults);
  const row = rows.find((r) => `clause:${clauseReference(r)}` === target);
  const body = bodySections(template).find((s) => `section:${sectionId(s)}` === target);
  const group = target === "title" || target === "closing" ? target : "body";
  const local = body ? body.style : row ? clauseStyle(template.defaults, row) : template.formatting[group];
  const style = { ...template.formatting.body, ...local };
  function change(patch: PdfEditorTextStyle, clear = false) {
    if (body) { onChange({ ...template, sections: template.sections.map((s) => sectionId(s) === sectionId(body) ? { ...s, style: clear ? {} : { ...s.style, ...patch } } : s) }); return; }
    if (!row) { onChange({ ...template, formatting: { ...template.formatting, [group]: clear ? clone(defaultTemplate.formatting[group]) : { ...local, ...patch } } }); return; }
    const defaults = clone(template.defaults), { index, subIndex, subsubIndex } = row;
    if (subsubIndex !== undefined) {
      defaults.subsubparagraphStyles = defaults.paragraphs.map((_, i) => (defaults.subparagraphs[i] || []).map((_, j) => (defaults.subsubparagraphs?.[i]?.[j] || []).map((_, k) => i === index && j === subIndex && k === subsubIndex ? clear ? {} : { ...local, ...patch } : defaults.subsubparagraphStyles?.[i]?.[j]?.[k] || {})));
    } else if (subIndex !== undefined) {
      defaults.subparagraphStyles = defaults.paragraphs.map((_, i) => (defaults.subparagraphs[i] || []).map((_, j) => i === index && j === subIndex ? clear ? {} : { ...local, ...patch } : defaults.subparagraphStyles?.[i]?.[j] || {}));
    } else defaults.paragraphStyles = defaults.paragraphs.map((_, i) => i === index ? clear ? {} : { ...local, ...patch } : defaults.paragraphStyles?.[i] || {});
    onChange({ ...template, defaults });
  }
  return <section className="template-typography" aria-label="Template typography">
    <div className="panel-divider" /><h3>Typography</h3>
    {row ? <TemplateTextField label="Default clause text" fields={template.apiFields} value={row.text} onChange={(text) => onChange({ ...template, defaults: { ...template.defaults, ...setClauseText(template.defaults, row, text) } })} /> : null}
    <label>Apply formatting to<select aria-label="Formatting target" value={target} onChange={(e) => setTarget(e.target.value)}><option value="body">Document body</option><option value="title">Document title</option><option value="closing">Closing</option>{bodySections(template).length > 1 ? <optgroup label="Text and terms sections">{bodySections(template).map((s) => <option key={sectionId(s)} value={`section:${sectionId(s)}`}>{s.label}</option>)}</optgroup> : null}<optgroup label="Default clauses">{rows.map((r) => <option key={clauseReference(r)} value={`clause:${clauseReference(r)}`}>{r.subsubIndex !== undefined ? "Subsubclause" : r.subIndex !== undefined ? "Subparagraph" : "Paragraph"} {clauseReference(r)}</option>)}</optgroup></select></label>
    <label>Font family<select aria-label="Font family" value={style.fontFamily || "Arial"} onChange={(e) => change({ fontFamily: e.target.value as PdfEditorTextStyle["fontFamily"] })}>{["Arial", "Georgia", "Times New Roman", "Verdana", "Courier New"].map((font) => <option key={font}>{font}</option>)}</select></label>
    <div className="field-pair"><label>Font size<select aria-label="Font size" value={style.fontSize || 11} onChange={(e) => change({ fontSize: Number(e.target.value) })}>{[8, 9, 10, 10.65, 11, 12, 14, 15, 16, 18, 20, 24, 28, 32, 36].map((size) => <option key={size}>{size}</option>)}</select></label><label>Text colour<input type="color" aria-label="Text colour" value={style.color || "#17252f"} onChange={(e) => change({ color: e.target.value })} /></label></div>
    <div className="format-controls template-format-buttons"><button className="format-bold" aria-label="Bold" aria-pressed={!!style.bold} onClick={() => change({ bold: !style.bold })}>B</button><button className="format-italic" aria-label="Italic" aria-pressed={!!style.italic} onClick={() => change({ italic: !style.italic })}>I</button><button className="format-underline" aria-label="Underline" aria-pressed={!!style.underline} onClick={() => change({ underline: !style.underline })}>U</button><button aria-label="Clear formatting" title="Reset this target's formatting" onClick={() => change({}, true)}>Tₓ</button></div>
    <label>Text alignment<select aria-label="Text alignment" value={style.align || "left"} onChange={(e) => change({ align: e.target.value as PdfEditorTextStyle["align"] })}>{["left", "center", "right", "justify"].map((align) => <option key={align} value={align}>{align === "center" ? "Centre" : align[0].toUpperCase() + align.slice(1)}</option>)}</select></label>
    <label>Line spacing<select aria-label="Line spacing" value={style.lineHeight || defaultTemplate.formatting.body.lineHeight} onChange={(e) => change({ lineHeight: Number(e.target.value) })}>{[1, 1.15, 1.2, 1.34, 1.5, 2, 2.5].map((height) => <option key={height}>{height}</option>)}</select></label>
  </section>;
}
