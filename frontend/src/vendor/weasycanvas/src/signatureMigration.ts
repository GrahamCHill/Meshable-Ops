import { defaultSignature, signatureMarker } from "./media";
import { normalizeSignatureIds } from "../editor/index";

const object = (value: unknown): Record<string, unknown> | null => value && typeof value === "object" && !Array.isArray(value) ? value as Record<string, unknown> : null;
const withLine = (value: string) => signatureMarker(value) ? value : `${value.trimEnd()}\n\n{{signature}}`;
function withIds(value: unknown, fallback?: unknown) {
  if (!Array.isArray(value) || !value.every(object)) return value;
  try {
    const fields = value.map((field, index) => ({ ...field, ...(field.id === undefined && Array.isArray(fallback) && fallback[index]?.id ? { id: fallback[index].id } : {}) }));
    const next = normalizeSignatureIds(fields);
    return value.every((field, index) => field.id === next[index].id) ? value : next;
  } catch { return value; }
}

/** Read older saved/exported templates without keeping a standalone signature section. */
function migrateStandaloneTemplate(value: unknown): unknown {
  const template = object(value), defaults = object(template?.defaults);
  if (!template || !defaults || typeof defaults.closing_text !== "string" || !Array.isArray(template.sections)) return value;
  const legacy = template.sections.map(object).find((s) => s?.kind === "signature");
  if (!legacy) return value;
  const closingText = withLine(defaults.closing_text);
  const sections = template.sections.filter((s) => object(s)?.kind !== "signature");
  if (!sections.some((s) => object(s)?.kind === "closing")) {
    const footer = sections.findIndex((s) => object(s)?.kind === "footer");
    sections.splice(footer < 0 ? sections.length : footer, 0, { kind: "closing", label: "Closing", required: true, locked: false, maxLength: 4000 });
  }
  const signing = object(template.signature);
  return { ...template, defaults: { ...defaults, closing_text: closingText }, sections: sections.map((s) => { const section = object(s); return section?.kind === "closing" ? { ...section, maxLength: Math.max(Number(section.maxLength) || 4000, closingText.length) } : s; }),
    signature: { ...defaultSignature, ...signing, required: signing?.required === true || legacy.required !== false } };
}

/** Keep the actual signature image/date and move its field into the document's closing. */
function migrateStandaloneDocument(value: unknown): unknown {
  const document = object(value), original = object(document?.template);
  if (!document || !original || !Array.isArray(original.sections) || !original.sections.some((s) => object(s)?.kind === "signature") || typeof document.closing_text !== "string") return value;
  const template = object(migrateStandaloneTemplate(original));
  if (!template || !Array.isArray(template.sections)) return value;
  const closingText = withLine(document.closing_text), signing = object(document.signature), settings = object(template.signature);
  return { ...document, template: { ...template, sections: template.sections.map((s) => { const section = object(s); return section?.kind === "closing" ? { ...section, maxLength: Math.max(Number(section.maxLength) || 4000, closingText.length) } : s; }) }, closing_text: closingText,
    signature: { ...defaultSignature, ...settings, ...signing, required: settings?.required === true || signing?.required === true } };
}

export function migrateSignatureTemplate(value: unknown): unknown {
  const migrated = migrateStandaloneTemplate(value), template = object(migrated);
  if (!template) return migrated;
  if (!("signature" in template)) { const signatures = withIds(template.signatures); return signatures === template.signatures ? migrated : { ...template, signatures }; }
  const { signature, ...rest } = template;
  return { ...rest, signatures: withIds(template.signatures ?? (signature ? [signature] : [])) };
}

export function migrateSignatureDocument(value: unknown): unknown {
  const migrated = migrateStandaloneDocument(value), document = object(migrated);
  if (!document) return migrated;
  const template = migrateSignatureTemplate(document.template);
  const signatures = withIds(document.signatures ?? (document.signature ? [document.signature] : []), object(template)?.signatures);
  if (template === document.template && !("signature" in document) && signatures === document.signatures) return migrated;
  const { signature, ...rest } = document;
  return { ...rest, template, signatures };
}
