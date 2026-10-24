import { useRef, useState, type PointerEvent } from "react";
import { fitImage, imageLocations, pageSize, type DocumentImage, type ImageSection, type PageText } from "./media";
import type { PageSettings } from "./templates";
import { StoredImage } from "../editor/index";

export function ImageRegion({ images, section, sectionId, sections, layout, text, editable = false, onChange = () => {} }: {
  images: DocumentImage[]; section: ImageSection; sectionId?: string; sections?: { id?: string; kind: string; label: string }[]; layout: PageSettings; text?: PageText; editable?: boolean; onChange?: (images: DocumentImage[]) => void;
}) {
  const [selected, setSelected] = useState<string | null>(null);
  const [error, setError] = useState("");
  const drag = useRef<{ image: DocumentImage; x: number; y: number; pxPerMm: number; resize: boolean } | null>(null);
  function update(image: DocumentImage) { try { const next = fitImage(image, layout, images, text); onChange(images.map((i) => i.id === image.id ? next : i)); setError(""); } catch (e) { setError(e instanceof Error ? e.message : "Could not resize image."); } }
  function start(event: PointerEvent<HTMLElement>, image: DocumentImage, resize = false) {
    if (!editable || event.button !== 0) return;
    event.preventDefault(); event.stopPropagation(); setSelected(image.id);
    const frame = event.currentTarget.closest<HTMLElement>(".document-image-frame")!;
    drag.current = { image, x: event.clientX, y: event.clientY, pxPerMm: frame.getBoundingClientRect().width / image.widthMm, resize };
    event.currentTarget.setPointerCapture(event.pointerId);
  }
  function move(event: PointerEvent<HTMLElement>) {
    const state = drag.current; if (!state) return;
    const dx = (event.clientX - state.x) / state.pxPerMm, dy = (event.clientY - state.y) / state.pxPerMm;
    const image = state.image;
    if (state.resize) {
      const ratio = image.widthMm / image.heightMm;
      const delta = Math.abs(dx) > Math.abs(dy * ratio) ? dx : dy * ratio;
      const widthMm = Math.max(8, 2 * ratio, image.widthMm + delta);
      update({ ...image, widthMm, heightMm: widthMm / ratio });
    } else update({ ...image, xMm: image.xMm + dx });
  }
  const stop = () => { drag.current = null; };
  const maxWidth = pageSize(layout).width - layout.marginLeft - layout.marginRight;
  const bodies = sections?.filter((s) => s.kind === "body" || s.kind === "text") || [{ id: "body", kind: "body", label: "Terms" }];
  const primary = bodies.find((s) => s.kind === section)?.id || section;
  return <div className="document-images" data-image-region={section} data-image-section-id={sectionId}>{error ? <span className="image-error" role="alert">{error}</span> : null}{images.filter((i) => i.section === section && (!["body", "text"].includes(section) || (i.sectionId || primary) === (sectionId || primary))).map((image) => <div className="document-image-row" key={image.id}>
    <div className={`document-image-frame ${editable ? "image-editable" : ""} ${selected === image.id ? "image-selected" : ""}`} style={{ width: `${image.widthMm}mm`, height: `${image.heightMm}mm`, marginLeft: `${image.xMm}mm` }}>
      <StoredImage source={image} alt={image.name} onSourceError={(error) => setError(`${image.name}: ${error.message}`)} draggable={false} tabIndex={editable ? 0 : undefined} onFocus={() => setSelected(image.id)} onPointerDown={(e) => start(e, image)} onPointerMove={move} onPointerUp={stop} onPointerCancel={stop}
        onKeyDown={(e) => { if (!editable) return; if (e.key === "ArrowLeft" || e.key === "ArrowRight") { e.preventDefault(); update({ ...image, xMm: image.xMm + (e.key === "ArrowLeft" ? -1 : 1) }); } }} />
      {editable ? <>
        <button type="button" className="image-resize-handle" aria-label={`Resize ${image.name}`} title="Drag to resize · use arrow keys for small adjustments" onPointerDown={(e) => start(e, image, true)} onPointerMove={move} onPointerUp={stop} onPointerCancel={stop}
          onKeyDown={(e) => { if (["ArrowLeft", "ArrowDown", "ArrowUp", "ArrowRight"].includes(e.key)) { e.preventDefault(); const widthMm = Math.max(8, image.widthMm + (["ArrowLeft", "ArrowDown"].includes(e.key) ? -1 : 1)); update({ ...image, widthMm, heightMm: widthMm * image.heightMm / image.widthMm }); } }}>↘</button>
        <div className="image-controls" onPointerDown={(e) => e.stopPropagation()}>
          <span>{Math.round(image.widthMm)} × {Math.round(image.heightMm)} mm</span>
          <select aria-label={`Placement of ${image.name}`} value={["body", "text"].includes(image.section) ? `content:${image.sectionId || primary}` : image.section} onChange={(e) => { const value = e.target.value, content = value.startsWith("content:") ? bodies.find((s) => (s.id || s.kind) === value.slice(8)) : null; update({ ...image, section: content ? content.kind as ImageSection : value as ImageSection, sectionId: content ? content.id || content.kind : undefined }); }}>{imageLocations.filter((l) => !["body", "text"].includes(l.value)).map((location) => <option key={location.value} value={location.value}>{location.label}</option>)}{bodies.map((s) => <option key={s.id || s.kind} value={`content:${s.id || s.kind}`}>{s.label}</option>)}</select>
          {(["left", "center", "right"] as const).map((align) => <button key={align} type="button" aria-label={`Align ${image.name} ${align}`} title={`Align ${align}`} onClick={() => update({ ...image, xMm: align === "left" ? 0 : (maxWidth - image.widthMm) / (align === "center" ? 2 : 1) })}>{align === "left" ? "⇤" : align === "right" ? "⇥" : "↔"}</button>)}
          <button type="button" aria-label={`Remove ${image.name}`} title="Remove image" onClick={() => { onChange(images.filter((i) => i.id !== image.id)); setSelected(null); }}>×</button>
        </div>
      </> : null}
    </div>
  </div>)}</div>;
}
