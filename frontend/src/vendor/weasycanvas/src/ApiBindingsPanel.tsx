import { resolveApiField, type PdfEditorApiField } from "../editor/index";
import { bindingLimit, bindingSlots } from "./apiBindings";
import { isContentSection, sectionId, sectionIds, sectionRules } from "./paragraphSections";
import type { DocumentTemplate, TemplateSection } from "./templates";
import { renameInlineField } from "./inlineFields";

export function newApiField(template: DocumentTemplate, maxLength = 4000): PdfEditorApiField {
  let index = 1; while (template.apiFields?.some((f) => f.key === `field${index}`)) index++;
  return { key: `field${index}`, label: `data field ${index}`, required: true, maxLength, fallback: "" };
}
export function ApiBindingsPanel({ template, section, onChange }: { template: DocumentTemplate; section: TemplateSection; onChange: (value: DocumentTemplate) => void }) {
  const fields = template.apiFields || [], id = sectionId(section);
  const indices = sectionIds(template.defaults, template).flatMap((value, i) => value === id ? [i] : []);
  const canBindText = !isContentSection(section) || indices.length === 1 && !template.defaults.subparagraphs[indices[0]]?.length && !template.defaults.subsubparagraphs?.[indices[0]]?.flat().length;
  function bind(slot: string, key: string) {
    const created = key === "__new" ? newApiField(template, bindingLimit(section, slot)) : undefined;
    const bindings = { ...section.bindings, [slot]: created?.key || key }; if (!key) delete bindings[slot as keyof typeof bindings];
    onChange({ ...template, ...(created ? { apiFields: [...fields, created] } : {}), sections: template.sections.map((s) => sectionId(s) === id ? { ...s, bindings,
      ...(slot === "text" && key && isContentSection(s) ? { rules: { ...sectionRules(template, s), minParagraphs: 1, maxParagraphs: 1, allowSubparagraphs: false, allowSubsubparagraphs: false } } : {}) } : s) });
  }
  function update(field: PdfEditorApiField, patch: Partial<PdfEditorApiField>) {
    const next = { ...template, apiFields: fields.map((f) => f.key === field.key ? { ...f, ...patch } : f),
      ...(patch.key !== undefined ? { sections: template.sections.map((s) => ({ ...s, ...(s.bindings ? { bindings: Object.fromEntries(Object.entries(s.bindings).map(([slot, key]) => [slot, key === field.key ? patch.key : key])) } : {}) })) } : {}) };
    onChange(patch.key !== undefined ? renameInlineField(next, field.key, patch.key) : next);
  }
  return <div className="api-bindings-panel"><div className="panel-divider" /><h3>Data sources</h3><p className="muted">data fields display values supplied by the host application. Readers cannot edit them.</p>
    {bindingSlots(section).map((slot) => {
      const key = section.bindings?.[slot], field = fields.find((f) => f.key === key), limit = bindingLimit(section, slot);
      return <div className="api-binding" key={slot}>
        <label>{slot === "text" ? "Text source" : `${slot[0].toUpperCase()}${slot.slice(1)} source`}<select aria-label={`Data source for ${slot}`} value={key || ""} onChange={(e) => bind(slot, e.target.value)}>
          <option value="">User edits text</option>{fields.map((f) => <option key={f.key} value={f.key} disabled={f.maxLength > limit || slot === "text" && !canBindText}>{f.label} · {f.key}</option>)}
          <option value="__new" disabled={fields.length >= 50 || slot === "text" && !canBindText}>+ New data field</option>
        </select></label>
        {slot === "text" && !canBindText ? <p className="muted">For supplied text, add an data field block or use a section containing one paragraph with no nested clauses.</p> : null}
        {field ? <div className="api-field-settings">
          <label>field key<input aria-label={`field key for ${slot}`} maxLength={120} value={field.key} onChange={(e) => update(field, { key: e.target.value })} /></label>
          <label>Field label<input aria-label={`data field label for ${slot}`} maxLength={80} value={field.label} onChange={(e) => update(field, { label: e.target.value })} /></label>
          <label className="check-row"><input aria-label={`Require field value for ${slot}`} type="checkbox" checked={field.required} onChange={(e) => update(field, { required: e.target.checked })} />Require field value</label>
          <label>Character limit<input aria-label={`API character limit for ${slot}`} type="number" min={1} max={limit} value={field.maxLength} onChange={(e) => update(field, { maxLength: Math.max(1, Math.min(limit, Number(e.target.value))) })} /></label>
          <label>Fallback text<textarea aria-label={`API fallback for ${slot}`} rows={2} maxLength={field.maxLength} value={field.fallback || ""} onChange={(e) => update(field, { fallback: e.target.value })} /></label>
          <p className="muted">{field.required ? "A missing or blank field value blocks PDF export. Fallback text is for preview." : "Fallback is used when the field key is missing or null."} {resolveApiField(field).value ? "" : "The field can appear empty until data is supplied."}</p>
          <button aria-label={`Remove data field ${field.key}`} onClick={() => onChange({ ...template, apiFields: fields.filter((f) => f.key !== field.key), sections: template.sections.map((s) => ({ ...s, ...(s.bindings ? { bindings: Object.fromEntries(Object.entries(s.bindings).filter(([, key]) => key !== field.key)) } : {}) })) })}>Remove data field</button>
        </div> : null}
      </div>;
    })}
  </div>;
}
