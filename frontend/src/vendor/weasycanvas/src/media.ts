import type { PageSettings } from "./templates";
import { normalizeSignatureIds, validSignatureId, validImageDataUrl, isImageSource, type PdfEditorSignatureSettings, type PdfEditorSignature, type PdfEditorImageSource } from "../editor/index";

export type ImageSection = "header" | "title" | "details" | "text" | "body" | "closing" | "footer";
export type DocumentImage = { id: string; name: string; section: ImageSection; sectionId?: string; widthMm: number; heightMm: number; xMm: number } & PdfEditorImageSource;
export type SignatureSettings = PdfEditorSignatureSettings;
export type DocumentSignature = PdfEditorSignature;
export const defaultSignature: SignatureSettings = { label: "Signature", signerName: "", required: false, widthMm: 70, align: "left" };
export const imageLocations: { value: ImageSection; label: string }[] = [
  { value: "header", label: "Header" }, { value: "footer", label: "Footer" }, { value: "text", label: "Paragraphs" }, { value: "body", label: "Terms" },
  { value: "title", label: "Title" }, { value: "details", label: "Document details" }, { value: "closing", label: "Closing" },
];
export const signatureMarkerPattern = /\{\{signature\}\}|^[ \t]*(?:(?:signature|signed(?: by)?|sign here)[ \t]*:?[ \t]*)?_{5,}[ \t]*$/im;
export const signatureMarker = (text: string) => text.match(signatureMarkerPattern);
export function signatureFields(closing: string, signatures?: SignatureSettings[]): DocumentSignature[] {
  if (!signatureMarker(closing)) return [];
  const fields = signatures?.length ? signatures : [{ ...defaultSignature }];
  try { return normalizeSignatureIds(fields); } catch { return fields; } // Keep invalid designer drafts editable; template validation reports errors.
}
export const signaturePosition = (index: number) => ["Top left", "Top right", "Bottom left", "Bottom right"][index];
export const signatureSettings = ({ id, label, signerName, required, widthMm, align }: SignatureSettings): SignatureSettings => ({ ...(id ? { id } : {}), label, signerName, required, widthMm, align });
export function pageSize(layout: PageSettings) {
  const [w, h] = layout.size === "Letter" ? [215.9, 279.4] : [210, 297];
  return layout.orientation === "landscape" ? { width: h, height: w } : { width: w, height: h };
}
export type PageText = { header: string; footer: string };
export function pageText(organization: string, reference: string, sections?: { kind: string }[], footer?: string): PageText {
  return { header: !sections || sections.some((s) => s.kind === "header") ? organization : "", footer: !sections || sections.some((s) => s.kind === "footer") ? footer ?? `${organization} · ${reference}` : "" };
}
const extraLineHeight = (text = "", size: number) => (text.split(/\r\n|\r|\n/).length - 1) * size * 1.4 * 25.4 / 96;
export function mediaMargins(layout: PageSettings, images: DocumentImage[] = [], text?: PageText) {
  const height = (region: ImageSection) => images.filter((i) => i.section === region).reduce((sum, i) => sum + i.heightMm + 3, 0);
  const top = height("header") + extraLineHeight(text?.header, 12), bottom = height("footer") + extraLineHeight(text?.footer, 10);
  return { top: Math.max(layout.marginTop, top ? 26 + top : 0), bottom: Math.max(layout.marginBottom, bottom ? 22 + bottom : 0) };
}
export function fitImage(image: DocumentImage, layout: PageSettings, others: DocumentImage[], text?: PageText): DocumentImage {
  const { width, height } = pageSize(layout);
  const margins = mediaMargins(layout, others.filter((i) => i.id !== image.id), text);
  const sameHeight = others.filter((i) => i.id !== image.id && i.section === image.section).reduce((sum, i) => sum + i.heightMm + 3, 0);
  const maxHeight = image.section === "header" ? Math.min(60, height - margins.bottom - 100 - 29 - sameHeight - extraLineHeight(text?.header, 12)) : image.section === "footer" ? Math.min(60, height - margins.top - 100 - 25 - sameHeight - extraLineHeight(text?.footer, 10)) : 100;
  if (maxHeight < 2) throw new Error("The header and footer need more writing space. Reduce or remove an image first.");
  const contentWidth = width - layout.marginLeft - layout.marginRight;
  const maxWidth = Math.min(180, contentWidth);
  const scale = Math.min(1, maxWidth / image.widthMm, maxHeight / image.heightMm);
  const widthMm = Math.round(image.widthMm * scale * 100) / 100, heightMm = Math.round(image.heightMm * scale * 100) / 100;
  if (widthMm < 6 || heightMm < 2) throw new Error("That image is too narrow for this section. Choose a different image or placement.");
  return { ...image, widthMm, heightMm, xMm: Math.round(Math.max(0, Math.min(contentWidth - widthMm, image.xMm)) * 100) / 100 };
}
export function validDataUrl(input: unknown): input is string {
  return validImageDataUrl(input);
}
export function isImages(input: unknown): input is DocumentImage[] {
  return Array.isArray(input) && input.length <= 8 && input.every((i) => i && Object.keys(i).every((key) => ["id", "name", "dataUrl", "assetKey", "section", "sectionId", "widthMm", "heightMm", "xMm"].includes(key)) && (i.sectionId === undefined || (i.section === "body" || i.section === "text") && typeof i.sectionId === "string" && /^[a-zA-Z0-9_-]{1,100}$/.test(i.sectionId)) && typeof i.id === "string" && i.id.length <= 100 && typeof i.name === "string" && i.name.length <= 160 && imageLocations.some((l) => l.value === i.section) &&
    [i.widthMm, i.heightMm, i.xMm].every((n) => typeof n === "number" && Number.isFinite(n)) && i.widthMm >= 6 && i.widthMm <= 180 && i.heightMm >= 2 && i.heightMm <= 100 && i.xMm >= 0 && i.xMm <= 280 && isImageSource(i)) &&
    new Set(input.map((i) => i.id)).size === input.length && input.reduce((sum, i) => sum + (i.dataUrl || "").length, 0) <= 1500000;
}
export function isSignatureSettings(input: unknown): input is SignatureSettings {
  const s = input as SignatureSettings;
  return !!s && (s.id === undefined || validSignatureId(s.id)) && typeof s.label === "string" && s.label.trim().length > 0 && s.label.length <= 100 && typeof s.signerName === "string" && s.signerName.length <= 160 && typeof s.required === "boolean" && typeof s.widthMm === "number" && Number.isFinite(s.widthMm) && s.widthMm >= 40 && s.widthMm <= 120 && ["left", "center", "right"].includes(s.align);
}
export function isSignature(input: unknown): input is DocumentSignature {
  const s = input as DocumentSignature;
  return isSignatureSettings(s) && Object.keys(s).every((key) => ["id", "label", "signerName", "required", "widthMm", "align", "dataUrl", "signedAt"].includes(key)) && (s.dataUrl === undefined || validDataUrl(s.dataUrl)) && (s.signedAt === undefined || (typeof s.signedAt === "string" && Number.isFinite(Date.parse(s.signedAt))));
}
export function isSignatures(input: unknown): input is DocumentSignature[] { return Array.isArray(input) && input.length <= 4 && input.every(isSignature) && new Set(input.flatMap(s => s.id ? [s.id] : [])).size === input.filter(s => s.id).length; }
export function isSignatureSettingsList(input: unknown): input is SignatureSettings[] {
  return Array.isArray(input) && input.length <= 4 && input.every((s) => isSignatureSettings(s) && Object.keys(s).every((key) => ["id", "label", "signerName", "required", "widthMm", "align"].includes(key))) && new Set(input.flatMap(s => s.id ? [s.id] : [])).size === input.filter(s => s.id).length;
}
export { prepareImageUpload as readImage } from "../editor/index";
