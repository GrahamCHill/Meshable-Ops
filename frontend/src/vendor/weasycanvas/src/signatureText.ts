export const signatureMarkerPattern = /\{\{signature\}\}|^[ \t]*(?:(?:signature|signed(?: by)?|sign here)[ \t]*:?[ \t]*)?_{5,}[ \t]*$/im;
export const signatureMarker = (text: string) => text.match(signatureMarkerPattern);

export function splitSignatureText(source: string) {
  const match = signatureMarker(source);
  return match ? { before: source.slice(0, match.index), marker: match[0], after: source.slice(match.index! + match[0].length) }
    : { before: source, marker: "", after: "" };
}

export function replaceClosingSegment(source: string, side: "before" | "after", text: string) {
  const parts = splitSignatureText(source);
  return side === "before" ? text + parts.marker + parts.after : parts.before + parts.marker + text;
}
