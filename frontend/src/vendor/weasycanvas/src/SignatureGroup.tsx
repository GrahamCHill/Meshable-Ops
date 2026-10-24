import { SignatureField } from "./SignatureField";
import { signaturePosition, type DocumentSignature } from "./media";

export function SignatureGroup({ signatures, editable = false, textEditable = false, onNameChange, onNameFocus, onSign, onClear, onRemove }: {
  signatures: DocumentSignature[]; editable?: boolean; textEditable?: boolean;
  onNameChange?: (index: number, name: string) => void; onNameFocus?: (index: number) => void;
  onSign?: (index: number) => void; onClear?: (index: number) => void; onRemove?: (index: number) => void;
}) {
  return <div className="signature-group">{Array.from({ length: Math.ceil(signatures.length / 2) }, (_, row) => <div className={`signature-row ${signatures.length > 1 ? "signature-pair" : ""}`} key={row}>
    {signatures.slice(row * 2, row * 2 + 2).map((signature, column) => {
      const index = row * 2 + column;
      return <div className="signature-cell" key={index} role="group" aria-label={`Signature ${index + 1}${signatures.length > 1 ? ` — ${signaturePosition(index)}` : ""}`}>
        <SignatureField signature={signatures.length > 1 ? { ...signature, align: column === 1 ? "right" : "left" } : signature} editable={editable} textEditable={textEditable} nameLabel={`Signature ${index + 1} name`} onNameChange={(name) => onNameChange?.(index, name)} onNameFocus={() => onNameFocus?.(index)} onSign={() => onSign?.(index)} onClear={() => onClear?.(index)} onRemove={onRemove ? () => onRemove(index) : undefined} />
      </div>;
    })}
  </div>)}</div>;
}
