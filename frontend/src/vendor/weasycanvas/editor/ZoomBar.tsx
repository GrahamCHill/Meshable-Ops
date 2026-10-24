import { useEffect, useRef, useState, type PointerEvent } from "react";

/** A movable bottom dock; its range input also supports keyboard zooming. */
export function ZoomBar({ zoom, onChange, pages, onFit, placement = "container" }: { zoom: number; onChange: (zoom: number) => void; pages: number; onFit: () => void; placement?: "container" | "viewport" }) {
  const [position, setPosition] = useState<{ x: number; y: number } | null>(null);
  const dock = useRef<HTMLDivElement>(null);
  const drag = useRef<{ x: number; y: number; left: number; top: number } | null>(null);
  const bounds = () => placement === "container" ? dock.current?.closest<HTMLElement>(".pdf-editor")?.getBoundingClientRect() || { left: 0, top: 0, width: window.innerWidth, height: window.innerHeight } : { left: 0, top: 0, width: window.innerWidth, height: window.innerHeight };
  const constrain = (point: { x: number; y: number }) => { const area = bounds(); return { x: Math.max(8, Math.min(area.width - (dock.current?.offsetWidth || 340) - 8, point.x)), y: Math.max(8, Math.min(area.height - (dock.current?.offsetHeight || 55) - 8, point.y)) }; };
  useEffect(() => {
    const resize = () => setPosition((point) => point ? constrain(point) : null);
    window.addEventListener("resize", resize);
    const observer = typeof ResizeObserver === "undefined" ? null : new ResizeObserver(resize), parent = dock.current?.closest(".pdf-editor");
    if (parent) observer?.observe(parent);
    return () => { window.removeEventListener("resize", resize); observer?.disconnect(); };
  }, [placement]);
  const change = (value: number) => onChange(Math.max(.4, Math.min(1.5, Math.round(value * 100) / 100)));
  function move(event: PointerEvent) {
    if (!drag.current || !dock.current) return;
    setPosition(constrain({ x: drag.current.left + event.clientX - drag.current.x, y: drag.current.top + event.clientY - drag.current.y }));
  }
  return <div ref={dock} className="pdf-editor-zoom-dock" aria-label="Zoom controls" style={position ? { left: position.x, top: position.y, bottom: "auto", transform: "none" } : undefined}>
    <button className="pdf-editor-zoom-handle" type="button" aria-label="Drag zoom bar" title="Drag or use arrow keys to move · double-click or Home to reset" onDoubleClick={() => setPosition(null)}
      onKeyDown={(event) => { if (event.key === "Home") { event.preventDefault(); setPosition(null); } if (["ArrowLeft", "ArrowRight", "ArrowUp", "ArrowDown"].includes(event.key) && dock.current) { event.preventDefault(); const rect = dock.current.getBoundingClientRect(), area = bounds(); setPosition(constrain({ x: rect.left - area.left + (event.key === "ArrowLeft" ? -12 : event.key === "ArrowRight" ? 12 : 0), y: rect.top - area.top + (event.key === "ArrowUp" ? -12 : event.key === "ArrowDown" ? 12 : 0) })); } }}
      onPointerDown={(event) => { const rect = dock.current!.getBoundingClientRect(), area = bounds(); drag.current = { x: event.clientX, y: event.clientY, left: rect.left - area.left, top: rect.top - area.top }; event.currentTarget.setPointerCapture(event.pointerId); }}
      onPointerMove={move} onPointerUp={() => { drag.current = null; }} onPointerCancel={() => { drag.current = null; }}>⠿</button>
    <span className="pdf-editor-zoom-pages">{pages} {pages === 1 ? "page" : "pages"}</span>
    <button type="button" aria-label="Zoom out" onClick={() => change(zoom - .1)} disabled={zoom <= .4}>−</button>
    <input aria-label="Document zoom" type="range" min="40" max="150" step="5" value={Math.round(zoom * 100)} onChange={(event) => change(Number(event.target.value) / 100)} />
    <button type="button" aria-label="Zoom in" onClick={() => change(zoom + .1)} disabled={zoom >= 1.5}>+</button>
    <button type="button" className="pdf-editor-zoom-value" title="Reset to 100%" onClick={() => onChange(1)}>{Math.round(zoom * 100)}%</button>
    <button type="button" className="pdf-editor-fit" onClick={onFit}>Fit</button>
  </div>;
}
