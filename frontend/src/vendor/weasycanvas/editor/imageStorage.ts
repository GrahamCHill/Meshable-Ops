export type PdfEditorImageSource = { dataUrl: string; assetKey?: null } | { assetKey: string; dataUrl?: null };
export type PdfEditorImageUpload = { name: string; dataUrl: string };
/** The host owns authentication and the storage namespace. Persist keys, never expiring URLs. */
export type PdfEditorImageStorage = {
  upload: (image: PdfEditorImageUpload, signal?: AbortSignal) => Promise<{ assetKey: string }>;
  load: (assetKey: string, signal?: AbortSignal) => Promise<Blob>;
};
export type HttpImageStorageOptions = {
  url?: string; headers?: HeadersInit | (() => HeadersInit); credentials?: RequestCredentials; fetch?: typeof fetch;
};
export const validAssetKey = (value: unknown): value is string => typeof value === "string" && value.length <= 240 && /^[a-zA-Z0-9][a-zA-Z0-9_./-]*$/.test(value) && value.split("/").every(part => !!part && part !== "." && part !== "..");
export const validImageDataUrl = (value: unknown): value is string => typeof value === "string" && value.length <= 350000 && /^data:image\/(?:png|jpeg);base64,[A-Za-z0-9+/]+={0,2}$/.test(value);
/** Normalize browser uploads before storing: bounded raster pixels, no remote resources. */
export async function prepareImageUpload(file: File): Promise<{ dataUrl: string; width: number; height: number }> {
  if (!["image/png", "image/jpeg", "image/webp"].includes(file.type) || file.size > 5 * 1024 * 1024) throw new Error("Choose a PNG, JPEG or WebP image up to 5 MB.");
  const bitmap = await createImageBitmap(file);
  try {
    if (bitmap.width * bitmap.height > 25000000 || bitmap.width / bitmap.height > 30 || bitmap.height / bitmap.width > 30) throw new Error("Choose an image under 25 megapixels with a less extreme aspect ratio.");
    const scale = Math.min(1, 1200 / Math.max(bitmap.width, bitmap.height));
    const canvas = document.createElement("canvas"); canvas.width = Math.max(1, Math.round(bitmap.width * scale)); canvas.height = Math.max(1, Math.round(bitmap.height * scale));
    canvas.getContext("2d")!.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
    let dataUrl = canvas.toDataURL(file.type === "image/jpeg" ? "image/jpeg" : "image/png", .85);
    if (dataUrl.length > 350000) {
      const small = document.createElement("canvas"); small.width = Math.max(1, Math.round(canvas.width * .65)); small.height = Math.max(1, Math.round(canvas.height * .65));
      small.getContext("2d")!.drawImage(canvas, 0, 0, small.width, small.height); dataUrl = small.toDataURL("image/png");
    }
    if (!validImageDataUrl(dataUrl)) throw new Error("This image is too detailed to store. Try a smaller image.");
    return { dataUrl, width: bitmap.width, height: bitmap.height };
  } finally { bitmap.close(); }
}
export function isImageSource(value: unknown): value is PdfEditorImageSource {
  if (!value || typeof value !== "object") return false;
  const source = value as { dataUrl?: unknown; assetKey?: unknown };
  return source.assetKey == null ? validImageDataUrl(source.dataUrl) : source.dataUrl == null && validAssetKey(source.assetKey);
}
export function validateImageBlob(blob: Blob) {
  if (!["image/png", "image/jpeg"].includes(blob.type.split(";")[0].toLowerCase()) || !blob.size || blob.size > 262480) throw new Error("Stored images must be PNG/JPEG raster images up to 262,480 bytes.");
  return blob;
}
async function failed(response: Response) {
  const body = await response.json().catch(() => null);
  throw new Error(typeof body?.error === "string" ? body.error : `Image storage request failed (${response.status}).`);
}
/** Compatible with the included /api/assets proxy, or a matching authenticated host API. */
export function createHttpImageStorage(options: HttpImageStorageOptions = {}): PdfEditorImageStorage {
  const base = (options.url || "/api/assets").replace(/\/$/, "");
  const headers = () => new Headers(typeof options.headers === "function" ? options.headers() : options.headers);
  return {
    async upload(image, signal) {
      if (!validImageDataUrl(image.dataUrl)) throw new Error("Upload a normalized PNG or JPEG image.");
      const requestHeaders = headers(); requestHeaders.set("Content-Type", "application/json"); requestHeaders.set("Accept", "application/json");
      const response = await (options.fetch || globalThis.fetch)(base, { method: "POST", headers: requestHeaders, credentials: options.credentials ?? "same-origin", signal, body: JSON.stringify(image) });
      if (!response.ok) await failed(response);
      const result = await response.json();
      if (!validAssetKey(result.assetKey)) throw new Error("Image storage returned an invalid asset key.");
      return { assetKey: result.assetKey };
    },
    async load(assetKey, signal) {
      if (!validAssetKey(assetKey)) throw new Error("Invalid image asset key.");
      const requestHeaders = headers(); requestHeaders.set("Accept", "image/png, image/jpeg");
      const response = await (options.fetch || globalThis.fetch)(`${base}/${assetKey.split("/").map(encodeURIComponent).join("/")}`, { headers: requestHeaders, credentials: options.credentials ?? "same-origin", signal });
      if (!response.ok) await failed(response);
      return validateImageBlob(await response.blob());
    },
  };
}
