import type { PdfEditorTextTarget } from "./types";

export function romanNumeral(number: number): string {
  let result = "";
  for (const [value, numeral] of [[10, "x"], [9, "ix"], [5, "v"], [4, "iv"], [1, "i"]] as const) while (number >= value) { result += numeral; number -= value; }
  return result;
}
export function clauseReference(target: PdfEditorTextTarget): string {
  return `${target.index + 1}${target.subIndex === undefined ? "" : String.fromCharCode(97 + target.subIndex)}${target.subsubIndex === undefined ? "" : `(${romanNumeral(target.subsubIndex + 1)})`}`;
}
