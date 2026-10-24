import type { ReactNode } from "react";
import type { PdfEditorApiField, PdfEditorApiValues } from "./apiFields";

/** Plain text shared with your generator. All other document fields belong to the host. */
export type PdfEditorContent = {
  paragraphs: string[];
  /** One stable section ID per top-level paragraph. Omit for a single body. */
  paragraphSectionIds?: string[];
  subparagraphs?: string[][];
  subsubparagraphs?: string[][][];
  paragraphStyles?: PdfEditorTextStyle[];
  subparagraphStyles?: PdfEditorTextStyle[][];
  subsubparagraphStyles?: PdfEditorTextStyle[][][];
};

export type PdfEditorTextStyle = {
  fontFamily?: "Arial" | "Georgia" | "Times New Roman" | "Verdana" | "Courier New";
  fontSize?: number;
  bold?: boolean;
  italic?: boolean;
  underline?: boolean;
  align?: "left" | "center" | "right" | "justify";
  color?: string;
  lineHeight?: number;
};

export type PdfEditorTextTarget = { index: number; subIndex?: number; subsubIndex?: number };

export type PdfEditorParagraphSection = {
  id: string; label: string; minParagraphs?: number; maxParagraphs?: number;
  maxSubparagraphs?: number; maxSubsubparagraphs?: number; maxTextLength?: number;
  editable?: boolean; numbered?: boolean; style?: PdfEditorTextStyle;
  /** General text has no markers, indentation or nested clause insertion. */
  presentation?: "text" | "clauses";
  /** Read-only data supplied by the host. Also disables structure changes within this section. */
  apiFieldKey?: string;
};

export type PdfEditorAdapter<TDocument> = {
  getContent: (document: TDocument) => PdfEditorContent;
  withContent: (document: TDocument, content: PdfEditorContent) => TDocument;
};

/** Host-owned permissions for one embedded editor. Omitted lists allow nothing. */
export type PdfEditorEditPolicy = {
  /** Stable IDs supplied to InlineText.fieldId or context.canEditField(). */
  fields?: readonly string[];
  /** Stable paragraph section IDs; permits text in that section and its descendants. */
  sections?: readonly string[];
  /** Allow insertion, movement and deletion in permitted sections. Defaults to false. */
  structure?: boolean;
};

export type PdfEditorEditPermissions = {
  canEditField: (fieldId?: string) => boolean;
  canEditSection: (sectionId: string) => boolean;
  canChangeStructure: (sectionId: string) => boolean;
};

export type PdfEditorContext<TDocument> = PdfEditorEditPermissions & {
  document: TDocument;
  onChange: (document: TDocument) => void;
  /** Global editing state. Use the permission helpers for individual controls. */
  editable: boolean;
};

/** Dimensions in mm; text measurements and spacing in CSS pixels. Match your @page CSS. */
export type PdfEditorPageLayout = {
  widthMm?: number;
  heightMm?: number;
  marginTopMm?: number;
  marginRightMm?: number;
  marginBottomMm?: number;
  marginLeftMm?: number;
  fontFamily?: string;
  fontSizePx?: number;
  lineHeight?: number;
  paragraphGapPx?: number;
};

export type PdfEditorLabels = {
  paragraph?: string;
  subparagraph?: string;
  subsubparagraph?: string;
  newParagraph?: string;
  newSubparagraph?: string;
  newSubsubparagraph?: string;
  details?: string;
  help?: string;
  page?: string;
};

/** Called only on request. The host owns its URL, authentication, payload and validation. */
export type PdfRenderer<TDocument> = (document: TDocument, signal: AbortSignal) => Promise<Blob>;

export type PdfEditorProps<TDocument> = {
  apiFields?: PdfEditorApiField[];
  apiValues?: PdfEditorApiValues;
  document: TDocument;
  onChange: (document: TDocument) => void;
  adapter: PdfEditorAdapter<TDocument>;
  /** Optional separate history checkpoint for insertion, movement and deletion. */
  onStructureChange?: (document: TDocument) => void;
  editable?: boolean;
  /** Omit for unrestricted editing, or provide an allowlist for this instance. */
  editPolicy?: PdfEditorEditPolicy;
  className?: string;
  pageLayout?: PdfEditorPageLayout;
  labels?: PdfEditorLabels;
  maxParagraphs?: number;
  maxSubparagraphs?: number;
  maxSubsubparagraphs?: number;
  maxTextLength?: number;
  initialZoom?: number;
  showRuler?: boolean;
  showToolbar?: boolean;
  /** Keep zoom/PDF preview inside this editor when embedded. */
  overlayPlacement?: "container" | "viewport";
  minParagraphs?: number;
  numbered?: boolean;
  bodyStyle?: PdfEditorTextStyle;
  /** Independent paragraph groups; IDs match content.paragraphSectionIds. */
  paragraphSections?: PdfEditorParagraphSection[];
  activeParagraphSectionId?: string;
  /** Measured flow content before a top-level paragraph (headings, images, etc.). */
  renderBeforeParagraph?: (context: PdfEditorContext<TDocument>, index: number) => ReactNode;
  paragraphEditable?: boolean;
  onActiveTextChange?: (target: PdfEditorTextTarget) => void;
  renderHeader?: (context: PdfEditorContext<TDocument>) => ReactNode;
  renderFooter?: (context: PdfEditorContext<TDocument>) => ReactNode;
  renderIntroduction?: (context: PdfEditorContext<TDocument>) => ReactNode;
  renderClosing?: (context: PdfEditorContext<TDocument>) => ReactNode;
  renderDetails?: (context: PdfEditorContext<TDocument>) => ReactNode;
  renderToolbarActions?: (context: PdfEditorContext<TDocument>) => ReactNode;
  renderPdf?: PdfRenderer<TDocument>;
  filename?: string;
  previewTitle?: string;
};
