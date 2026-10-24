import { useState } from "react";
import { inlineApiKeys } from "../editor/index";
import { newApiField } from "./ApiBindingsPanel";
import { inlineEntries, renameInlineField } from "./inlineFields";
import type { DocumentTemplate } from "./templates";

export function InlineApiPanel({ template, onChange }: { template: DocumentTemplate; onChange: (value: DocumentTemplate) => void }) {
  const [selected, setSelected] = useState("");
  const field = template.apiFields?.find((f) => f.key === selected) || template.apiFields?.[0];
  function add() { const field = newApiField(template); onChange({ ...template, apiFields: [...(template.apiFields || []), field] }); setSelected(field.key); }
  function update(patch: Partial<NonNullable<DocumentTemplate["apiFields"]>[number]>) {
    if (!field) return;
    let next: DocumentTemplate = { ...template, apiFields: template.apiFields?.map((f) => f.key === field.key ? { ...f, ...patch } : f) };
    if (patch.key !== undefined) { next = renameInlineField({ ...next, sections: next.sections.map((s) => ({ ...s, ...(s.bindings ? { bindings: Object.fromEntries(Object.entries(s.bindings).map(([slot, key]) => [slot, key === field.key ? patch.key : key])) } : {}) })) }, field.key, patch.key); setSelected(patch.key); }
    onChange(next);
  }
  const usedInline = field && inlineEntries(template, template.defaults).some((entry) => inlineApiKeys(entry.value).includes(field.key));
  return <section className="inline-api-panel"><div className="panel-divider" /><h3>Inline data fields</h3><p className="muted">Insert a field at the cursor in a template text box. The value is protected; the surrounding sentence stays editable.</p>
    <button className="full-width" aria-label="Add inline data field" disabled={(template.apiFields?.length || 0) >= 50} onClick={add}>+ Define inline data field</button>
    {field ? <>
      <label>Field to configure<select aria-label="Inline data field" value={field.key} onChange={(event) => setSelected(event.target.value)}>{template.apiFields?.map((f) => <option key={f.key} value={f.key}>{f.label} · {f.key}</option>)}</select></label>
      <label>field key<input aria-label="field key for inline" maxLength={120} value={field.key} onChange={(event) => update({ key: event.target.value })} /></label>
      <label>Field label<input aria-label="data field label for inline" maxLength={80} value={field.label} onChange={(event) => update({ label: event.target.value })} /></label>
      <label className="check-row"><input aria-label="Require inline field value" type="checkbox" checked={field.required} onChange={(event) => update({ required: event.target.checked })} />Require field value</label>
      <label>Character limit<input aria-label="Inline API character limit" type="number" min={1} max={4000} value={field.maxLength} onChange={(event) => update({ maxLength: Math.max(1, Math.min(4000, Number(event.target.value))) })} /></label>
      <label>Fallback text<textarea aria-label="Inline API fallback" rows={2} maxLength={field.maxLength} value={field.fallback || ""} onChange={(event) => update({ fallback: event.target.value })} /></label>
      <button disabled={usedInline} onClick={() => onChange({ ...template, apiFields: template.apiFields?.filter((f) => f.key !== field.key), sections: template.sections.map((s) => ({ ...s, ...(s.bindings ? { bindings: Object.fromEntries(Object.entries(s.bindings).filter(([, key]) => key !== field.key)) } : {}) })) })}>Remove field definition</button>
      {usedInline ? <p className="muted">Remove its markers from template text before deleting the definition. Renaming the key updates existing markers.</p> : null}
    </> : null}
  </section>;
}
