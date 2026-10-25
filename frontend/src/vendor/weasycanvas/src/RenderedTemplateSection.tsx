import { InlineText } from "../editor/InlineText";
import { textStyle } from "../editor/formatting";
import { footerText, headingText } from "./documentText";
import { isContentSection } from "./paragraphSections";
import { SignatureGroup } from "./SignatureGroup";
import { signatureFields } from "./media";
import { splitSignatureText } from "./signatureText";
import { pageTextBinding, templatePageText, updateTemplatePageText, type TemplatePageTextSlot } from "./templatePageEditing";
import type { DocumentTemplate, TemplateSection } from "./templates";
import type { CustomDocument } from "./document";

export function RenderedTemplateSection({ template, section, preview, onChange, onSelect }: {
  template: DocumentTemplate; section: TemplateSection; preview: CustomDocument;
  onChange: (template: DocumentTemplate) => void; onSelect: () => void;
}) {
  function field(slot: TemplatePageTextSlot, label: string, resolved: string, maxLength = section.maxLength) {
    const key = pageTextBinding(section, slot), source = templatePageText(template, section, slot);
    return <div className="template-render-field"><InlineText label={label} value={key ? resolved : source} sourceKey={key} editable multiline maxLength={maxLength}
      placeholder={`Click to add ${label.toLowerCase()}`} onFocus={onSelect} onCommit={(text) => onChange(updateTemplatePageText(template, section, slot, text))} /></div>;
  }
  if (isContentSection(section)) return <div className="template-render-heading">{field("heading", `${section.label} heading`, headingText(preview, section), 80)}</div>;
  if (section.kind === "header") return <div className="mock-header">{field("organization", "Organisation", preview.organization, Math.min(160, section.maxLength))}</div>;
  if (section.kind === "title") return <div className="mock-title" style={textStyle({ ...template.formatting.body, ...template.formatting.title })}>{field("title", "Document title", preview.title)}</div>;
  if (section.kind === "details") return <div className="template-render-details">
    {field("referenceLabel", "Reference label", "", 80)}{field("reference", "Reference", preview.reference, Math.min(100, section.maxLength))}
    {field("recipientLabel", "Recipient label", "", 80)}{field("recipient", "Recipient", preview.recipient, Math.min(500, section.maxLength))}
  </div>;
  if (section.kind === "footer") return <div className="mock-footer">{field("footer", "Footer", footerText(preview))}</div>;
  if (section.kind === "closing") {
    const parts = splitSignatureText(template.defaults.closing_text), signatures = signatureFields(template.defaults.closing_text, template.signatures);
    const previewParts = splitSignatureText(preview.closing_text);
    return <div className="mock-closing" style={textStyle({ ...template.formatting.body, ...template.formatting.closing })}>
      {field("closingBefore", "Closing text", previewParts.before, Math.max(0, section.maxLength - (template.defaults.closing_text.length - parts.before.length)))}
      {signatures.length ? <SignatureGroup signatures={signatures} /> : null}
      {parts.marker && !section.bindings?.text ? field("closingAfter", "Text after signatures", previewParts.after, Math.max(0, section.maxLength - (template.defaults.closing_text.length - parts.after.length))) : null}
    </div>;
  }
  return null;
}
