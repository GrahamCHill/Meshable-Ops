"use client";

import { useLayoutEffect, useRef, useState } from "react";
import { useApiFields } from "./ApiFieldsContext";
import { useEditPermissions } from "./EditPolicyContext";
import { ProtectedText } from "./ProtectedText";
import { inlineApiKeys, sameInlineApiFields } from "./inlineApi";

export type InlineTextProps = {
  value: string;
  label: string;
  editable: boolean;
  onCommit: (value: string) => void;
  multiline?: boolean;
  className?: string;
  maxLength?: number;
  onFocus?: () => void;
  placeholder?: string;
  /** Host-owned API value; always read-only, even if the rest of the document is editable. */
  sourceKey?: string;
  /** Stable host field ID. Unnamed slot text is read-only when editPolicy is supplied. */
  fieldId?: string;
  /** Paragraph section ID; used by PdfEditor for body text, including nested clauses. */
  sectionId?: string;
};

export function InlineText({ value, label, editable: requestedEditable, onCommit, multiline = false, className = "", maxLength = 4000, onFocus, placeholder, sourceKey, fieldId, sectionId }: InlineTextProps) {
  const api = useApiFields();
  const permissions = useEditPermissions();
  const editable = requestedEditable && (sectionId === undefined ? permissions.canEditField(fieldId) : fieldId === undefined && permissions.canEditSection(sectionId));
  const ref = useRef<HTMLTextAreaElement>(null);
  const [draft, setDraft] = useState(value);
  const [previousValue, setPreviousValue] = useState(value);
  if (previousValue !== value) { setPreviousValue(value); setDraft(value); }
  useLayoutEffect(() => {
    if (ref.current) { ref.current.style.height = "0px"; ref.current.style.height = `${ref.current.scrollHeight}px`; }
  }, [draft, editable]);
  if (!sourceKey && inlineApiKeys(value).length) return <ProtectedText value={value} label={label} editable={editable} onCommit={onCommit} multiline={multiline} maxLength={maxLength} className={className} onFocus={onFocus} fields={api.fields} values={api.values} />;
  if (!editable || sourceKey) return <div className={`pdf-editor-text ${className}`} data-api-field={sourceKey} role={sourceKey ? "textbox" : undefined} aria-label={sourceKey ? label : undefined} aria-readonly={sourceKey ? true : undefined} tabIndex={sourceKey ? 0 : undefined} onFocus={sourceKey ? onFocus : undefined} title={sourceKey ? `Provided by API: ${sourceKey}` : undefined}>{value}</div>;
  return <textarea ref={ref} aria-label={label} title={label} placeholder={placeholder} spellCheck rows={1}
    className={`pdf-editor-inline ${className}`} value={draft} maxLength={maxLength} onFocus={onFocus}
    onChange={(event) => {
      if (!editable) return;
      const text = multiline ? event.target.value : event.target.value.replace(/\n/g, " ");
      if (!sameInlineApiFields(value, text)) return;
      setDraft(text);
      // Commit immediately so toolbar/keyboard exports always use the visible draft.
      onCommit(text);
    }}
    onKeyDown={(event) => { if (!multiline && event.key === "Enter") { event.preventDefault(); event.currentTarget.blur(); } }} />;
}
