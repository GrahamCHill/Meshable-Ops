/** Stable identities allow a host API to address each of the four signing slots. */
export type PdfEditorSignatureSettings = { id?: string; label: string; signerName: string; required: boolean; widthMm: number; align: "left" | "center" | "right" };
export type PdfEditorSignature = PdfEditorSignatureSettings & { dataUrl?: string; signedAt?: string };
export type PdfEditorSignatureUpdate = { signerName?: string; dataUrl?: string | null; signedAt?: string | null };
export type PdfEditorSignatureField = PdfEditorSignature & { id: string; index: number; row: number; column: "left" | "center" | "right"; signed: boolean };
export const validSignatureId = (id: unknown): id is string => typeof id === "string" && /^[a-zA-Z][a-zA-Z0-9_.-]{0,99}$/.test(id);
const validImage = (value: unknown): value is string => typeof value === "string" && value.length <= 350000 && /^data:image\/(?:png|jpeg);base64,[A-Za-z0-9+/]+={0,2}$/.test(value);

/** Legacy slots receive deterministic IDs. Persist the returned IDs before reordering fields. */
export function normalizeSignatureIds<T extends { id?: string }>(signatures: readonly T[]): (T & { id: string })[] {
  if (signatures.length > 4) throw new Error("Use at most four signature fields.");
  const reserved = new Set<string>();
  for (const field of signatures) if (field.id !== undefined) {
    if (!validSignatureId(field.id) || reserved.has(field.id)) throw new Error("Signature field IDs must be unique and start with a letter; use letters, numbers, dots, underscores or hyphens (up to 100 characters).");
    reserved.add(field.id);
  }
  return signatures.map((field, index) => {
    if (field.id) return { ...field, id: field.id };
    let sequence = index + 1; while (reserved.has(`signature-${sequence}`)) sequence++;
    const id = `signature-${sequence}`; reserved.add(id); return { ...field, id };
  });
}

export function getSignatureFields(signatures: readonly PdfEditorSignature[]): PdfEditorSignatureField[] {
  return normalizeSignatureIds(signatures).map((field, index) => ({ ...field, index, row: Math.floor(index / 2), column: signatures.length === 1 ? field.align : index % 2 ? "right" : "left", signed: !!field.dataUrl }));
}

/** Update only one existing slot's value. Layout, requirements, ID and other slots stay intact. */
export function updateSignatureField(signatures: readonly PdfEditorSignature[], id: string, update: PdfEditorSignatureUpdate | null): (PdfEditorSignature & { id: string })[] {
  const fields = normalizeSignatureIds(signatures), index = fields.findIndex(field => field.id === id);
  if (index < 0) throw new Error(`Unknown signature field: ${id}`);
  if (update !== null && (typeof update !== "object" || Array.isArray(update) || Object.keys(update).some(key => !["signerName", "dataUrl", "signedAt"].includes(key)))) throw new Error("A signature update accepts only signerName, dataUrl and signedAt.");
  const patch = update ?? { dataUrl: null, signedAt: null }, field = { ...fields[index] };
  if (patch.signerName !== undefined) {
    if (typeof patch.signerName !== "string" || patch.signerName.length > 160) throw new Error("Signer names allow up to 160 characters.");
    field.signerName = patch.signerName;
  }
  if (patch.dataUrl !== undefined) {
    if (patch.dataUrl !== null && !validImage(patch.dataUrl)) throw new Error("Use an embedded PNG or JPEG signature up to 350,000 characters.");
    if (patch.dataUrl === null) delete field.dataUrl; else field.dataUrl = patch.dataUrl;
    delete field.signedAt;
  }
  if (patch.signedAt !== undefined && patch.signedAt !== null) {
    if (typeof patch.signedAt !== "string" || !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?(?:Z|[+-]\d{2}:\d{2})$/.test(patch.signedAt) || !Number.isFinite(Date.parse(patch.signedAt))) throw new Error("Use an ISO signing timestamp with a timezone.");
    if (!field.dataUrl) throw new Error("A signing timestamp requires a signature image.");
    field.signedAt = patch.signedAt;
  } else if (patch.signedAt === null) delete field.signedAt;
  fields[index] = field; return fields;
}
