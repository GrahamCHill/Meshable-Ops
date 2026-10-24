import type { PdfRenderer } from "./types";

export type HttpPdfRendererOptions<TDocument> = {
  url: string;
  toPayload: (document: TDocument) => unknown;
  /** Dynamic headers allow the host to provide its current access token. */
  headers?: HeadersInit | (() => HeadersInit);
  credentials?: RequestCredentials;
  fetch?: typeof fetch;
};

/** JSON in, application/pdf out. Works with any HTTP wrapper around a WeasyPrint generator. */
export function createHttpPdfRenderer<TDocument>(options: HttpPdfRendererOptions<TDocument>): PdfRenderer<TDocument> {
  return async (document, signal) => {
    const headers = new Headers(typeof options.headers === "function" ? options.headers() : options.headers);
    headers.set("Content-Type", "application/json");
    headers.set("Accept", "application/pdf");
    const response = await (options.fetch || globalThis.fetch)(options.url, {
      method: "POST", headers, credentials: options.credentials ?? "same-origin", signal,
      body: JSON.stringify(options.toPayload(document)),
    });
    if (!response.ok) {
      let message = `PDF generation failed (${response.status}).`;
      if (response.headers.get("content-type")?.includes("application/json")) {
        const body = await response.json().catch(() => null);
        if (typeof body?.error === "string") message = body.error;
        else if (typeof body?.detail === "string") message = body.detail;
      }
      throw new Error(message);
    }
    const pdf = await response.blob();
    if (!pdf.size || pdf.type.split(";")[0].toLowerCase() !== "application/pdf") {
      throw new Error("The generator must return a non-empty application/pdf response.");
    }
    return pdf;
  };
}
