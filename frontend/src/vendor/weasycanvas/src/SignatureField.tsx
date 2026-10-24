import type { DocumentSignature } from "./media";
import { InlineText } from "../editor/index";

export function SignatureField({ signature, editable = false, textEditable = false, nameLabel = "Signature name", onNameChange, onNameFocus, onSign, onClear, onRemove }: {
  signature: DocumentSignature; editable?: boolean; textEditable?: boolean; nameLabel?: string;
  onNameChange?: (name: string) => void; onNameFocus?: () => void; onSign?: () => void; onClear?: () => void; onRemove?: () => void;
}) {
  const signedDate = signature.signedAt ? new Intl.DateTimeFormat("en-ZA", { year: "numeric", month: "short", day: "numeric", timeZone: "Africa/Johannesburg" }).format(new Date(signature.signedAt)) : "";
  return <div className="signature-field" data-signature-field data-signature-id={signature.id} style={{ width: `${signature.widthMm}mm`, marginLeft: signature.align === "right" ? "auto" : signature.align === "center" ? "auto" : 0, marginRight: signature.align === "center" ? "auto" : 0 }}>
    <div className="signature-ink-area">
      {signature.dataUrl ? <img className="signature-ink" src={signature.dataUrl} alt={`Signature${signature.signerName ? ` of ${signature.signerName}` : ""}`} /> : editable ? <button type="button" className="signature-sign-button" onClick={onSign}>✍ &nbsp; Sign here</button> : null}
    </div>
    <div className="signature-line" />
    <div className="signature-caption"><strong>{textEditable && onNameChange ? <InlineText fieldId={`signature:${signature.id}:name`} label={nameLabel} value={signature.signerName || signature.label} editable multiline maxLength={160} onFocus={onNameFocus} onCommit={onNameChange} /> : signature.signerName || signature.label}{signature.required ? " *" : ""}</strong><span>{signedDate}</span></div>
    {editable ? <div className="signature-actions">{signature.dataUrl ? <><button type="button" onClick={onSign}>Replace signature</button><button type="button" onClick={onClear}>Clear signature</button></> : null}{onRemove ? <button type="button" onClick={onRemove}>Remove line</button> : null}</div> : null}
  </div>;
}
