import type { PdfEditorTextStyle } from "../editor/index";
import type { PdfEditorApiValues } from "../editor/index";
import type { DocumentImage, DocumentSignature } from "./media";
import type { DocumentText } from "./documentText";
import { createDocumentFromTemplate, defaultTemplate, type DocumentTemplate, type PageSettings } from "./templates";

export type CustomDocument = {
  apiValues?: PdfEditorApiValues;
  text?: DocumentText;
  organization: string;
  title: string;
  reference: string;
  recipient: string;
  paragraphs: string[];
  paragraphSectionIds?: string[];
  subparagraphs: string[][];
  subsubparagraphs: string[][][];
  closing_text: string;
  template: DocumentTemplate;
  layout: PageSettings;
  formatting: { body: PdfEditorTextStyle; title: PdfEditorTextStyle; closing: PdfEditorTextStyle };
  paragraphStyles: PdfEditorTextStyle[];
  subparagraphStyles: PdfEditorTextStyle[][];
  subsubparagraphStyles: PdfEditorTextStyle[][][];
  numbered: boolean;
  images: DocumentImage[];
  signatures: DocumentSignature[];
};

export function sampleDocument(): CustomDocument {
  return createDocumentFromTemplate(defaultTemplate);
}
