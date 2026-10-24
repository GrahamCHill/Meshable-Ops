/** Host-supplied data. Keys are literal names, including dotted names; no paths are evaluated. */
export type PdfEditorApiValues = Record<string, string | number | boolean | null>;
export type PdfEditorApiField = {
  key: string;
  label: string;
  required: boolean;
  maxLength: number;
  fallback?: string;
};
const own = (value: object, key: string) => Object.prototype.hasOwnProperty.call(value, key);
export const validApiKey = (key: string) => /^[a-zA-Z][a-zA-Z0-9_.-]{0,119}$/.test(key) && !key.split(".").some((part) => ["__proto__", "prototype", "constructor"].includes(part));
export function isApiFields(input: unknown): input is PdfEditorApiField[] {
  return Array.isArray(input) && input.length <= 50 && new Set(input.map((f) => f?.key)).size === input.length && input.every((f) =>
    f && typeof f === "object" && !Array.isArray(f) && Object.keys(f).every((key) => ["key", "label", "required", "maxLength", "fallback"].includes(key)) &&
    typeof f.key === "string" && validApiKey(f.key) && typeof f.label === "string" && !!f.label.trim() && f.label.length <= 80 && typeof f.required === "boolean" &&
    Number.isInteger(f.maxLength) && f.maxLength >= 1 && f.maxLength <= 4000 && (f.fallback === undefined || typeof f.fallback === "string" && f.fallback.length <= f.maxLength));
}
export function isApiValues(input: unknown): input is PdfEditorApiValues {
  return !!input && typeof input === "object" && !Array.isArray(input) && Object.keys(input).length <= 50 && Object.entries(input).every(([key, value]) =>
    validApiKey(key) && (value === null || typeof value === "boolean" || typeof value === "number" && Number.isFinite(value) && Math.abs(value) <= Number.MAX_SAFE_INTEGER || typeof value === "string" && value.length <= 4000)) &&
    Object.values(input).reduce<number>((count, value) => count + String(value ?? "").length, 0) <= 60000;
}
export function resolveApiField(field: PdfEditorApiField, values: PdfEditorApiValues = {}) {
  const raw = own(values, field.key) ? values[field.key] : undefined;
  const missing = raw === undefined || raw === null || typeof raw === "string" && !raw.trim();
  const value = raw === undefined || raw === null ? field.fallback ?? "" : String(raw);
  return { value, missing, error: field.required && missing ? `${field.label}: API value '${field.key}' is required.` : value.length > field.maxLength ? `${field.label}: use at most ${field.maxLength} characters for API value '${field.key}'.` : undefined };
}
export function apiFieldErrors(fields: PdfEditorApiField[] = [], values: PdfEditorApiValues = {}) {
  if (!isApiFields(fields)) return ["API field definitions are invalid."];
  if (!isApiValues(values)) return ["API values must be a map of text, finite numbers, booleans or null (up to 50 keys and 60,000 characters)."];
  return fields.flatMap((field) => { const { error } = resolveApiField(field, values); return error ? [error] : []; });
}
