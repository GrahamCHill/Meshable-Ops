import type { CSSProperties } from "react";
import type { PdfEditorTextStyle } from "./types";

export function textStyle(style?: PdfEditorTextStyle): CSSProperties {
  return style ? {
    fontFamily: style.fontFamily, fontSize: style.fontSize ? `${style.fontSize * 4 / 3}px` : undefined,
    fontWeight: style.bold === undefined ? undefined : style.bold ? 700 : 400, fontStyle: style.italic === undefined ? undefined : style.italic ? "italic" : "normal",
    textDecoration: style.underline === undefined ? undefined : style.underline ? "underline" : "none", textAlign: style.align,
    color: style.color, lineHeight: style.lineHeight,
  } : {};
}
