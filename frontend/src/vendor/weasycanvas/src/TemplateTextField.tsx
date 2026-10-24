import { useRef, useState } from "react";
import type { PdfEditorApiField } from "../editor/index";
export function TemplateTextField({ label, ariaLabel = label, value, onChange, maxLength = 4000, rows = 4, fields = [] }: {
  label: string; ariaLabel?: string; value: string; onChange: (source: string) => void; maxLength?: number; rows?: number; fields?: PdfEditorApiField[];
}) {
  const input = useRef<HTMLTextAreaElement>(null), [key, setKey] = useState("");
  function insert() {
    if (!key || !input.current) return;
    const start = input.current.selectionStart, end = input.current.selectionEnd, token = `{{api:${key}}}`;
    const next = value.slice(0, start) + token + value.slice(end); if (next.length > maxLength) return;
    onChange(next); requestAnimationFrame(() => { input.current?.focus(); input.current?.setSelectionRange(start + token.length, start + token.length); });
  }
  return <div className="template-text-field"><label>{label}<textarea ref={input} aria-label={ariaLabel} value={value} rows={rows} maxLength={maxLength} onChange={(event) => onChange(event.target.value)} /></label>
    {fields.length ? <div className="inline-api-insert"><select aria-label={`data field for ${ariaLabel}`} value={key} onChange={(event) => setKey(event.target.value)}><option value="">Choose data field…</option>{fields.map((field) => <option key={field.key} value={field.key}>{field.label} · {field.key}</option>)}</select><button type="button" aria-label={`Insert data field into ${ariaLabel}`} disabled={!fields.some((field) => field.key === key)} onClick={insert}>Insert field</button></div> : null}
  </div>;
}
