"use client";
import { useLayoutEffect, useRef, type ClipboardEvent, type KeyboardEvent } from "react";
import type { PdfEditorApiField, PdfEditorApiValues } from "./apiFields";
import { inlineApiParts, inlineApiValue, sameInlineApiFields } from "./inlineApi";

/** Serialize only plain text and immutable field identities; never serialize API values or HTML. */
function sourceText(node: Node): string {
  if (node.nodeType === Node.TEXT_NODE) return node.textContent || "";
  if (node instanceof HTMLElement) {
    if (node.hasAttribute("data-editor-sentinel")) return "";
    if (node.dataset.apiToken) return `{{api:${node.dataset.apiToken}}}`;
    if (node.tagName === "BR") return "\n";
  }
  return Array.from(node.childNodes).map((child, index) => (index && child instanceof HTMLElement && ["DIV", "P"].includes(child.tagName) ? "\n" : "") + sourceText(child)).join("");
}
function selectionRange(root: HTMLElement): Range | null {
  const selection = window.getSelection(); if (!selection?.rangeCount) return null;
  const range = selection.getRangeAt(0);
  return root.contains(range.startContainer) && root.contains(range.endContainer) ? range : null;
}
function offsetAt(root: HTMLElement, node: Node, offset: number) {
  const range = document.createRange(); range.selectNodeContents(root); range.setEnd(node, offset); return sourceText(range.cloneContents()).length;
}
function setCaret(root: HTMLElement, at: number) {
  const range = document.createRange(); let remaining = Math.max(0, at), placed = false;
  const visit = (node: Node) => {
    if (placed) return;
    if (node instanceof HTMLElement && node.dataset.apiToken) {
      const length = `{{api:${node.dataset.apiToken}}}`.length;
      if (remaining < length) { range.setStartBefore(node); placed = true; } else remaining -= length;
    } else if (node.nodeType === Node.TEXT_NODE) {
      const length = node.textContent?.length || 0;
      if (remaining <= length) { range.setStart(node, remaining); placed = true; } else remaining -= length;
    } else for (const child of Array.from(node.childNodes)) visit(child);
  };
  visit(root); if (!placed) { range.selectNodeContents(root); range.collapse(false); } else range.collapse(true);
  const selection = window.getSelection(); selection?.removeAllRanges(); selection?.addRange(range);
}
function touchesField(root: HTMLElement, range: Range) {
  return Array.from(root.querySelectorAll<HTMLElement>("[data-api-token]")).some((span) => range.intersectsNode(span) && (!range.collapsed || span.contains(range.startContainer)));
}

export type ProtectedTextProps = {
  value: string; label: string; editable: boolean; onCommit: (source: string) => void;
  fields: PdfEditorApiField[]; values: PdfEditorApiValues; multiline: boolean; maxLength: number;
  className: string; onFocus?: () => void;
};
export function ProtectedText(props: ProtectedTextProps) {
  const root = useRef<HTMLDivElement>(null), current = useRef(props), composing = useRef(false);
  current.current = props;
  function paint(source: string, caret?: number) {
    const element = root.current; if (!element) return;
    const fragment = document.createDocumentFragment();
    for (const part of inlineApiParts(source)) {
      if (!part.key) fragment.append(document.createTextNode(part.text));
      else {
        const span = document.createElement("span"); span.dataset.apiToken = part.key; span.contentEditable = "false"; span.draggable = false;
        span.className = "pdf-editor-api-token"; span.setAttribute("aria-label", `${current.current.fields.find((f) => f.key === part.key)?.label || part.key}: provided by API`);
        span.title = `Provided by API: ${part.key}`; span.textContent = inlineApiValue(part.key, current.current.fields, current.current.values) || "\u2060"; fragment.append(span);
      }
    }
    if (source.endsWith("\n")) { const sentinel = document.createElement("br"); sentinel.dataset.editorSentinel = ""; fragment.append(sentinel); }
    element.replaceChildren(fragment); if (caret !== undefined) setCaret(element, caret);
  }
  function commitInput() {
    const element = root.current, latest = current.current; if (!element || composing.current) return;
    if (!latest.editable) { paint(latest.value); return; }
    const next = sourceText(element), range = selectionRange(element), at = range ? offsetAt(element, range.startContainer, range.startOffset) : undefined;
    if (!sameInlineApiFields(latest.value, next) || next.length > latest.maxLength) { paint(latest.value, at); return; }
    // Browser replacements must never change the visible data inside an API span.
    for (const span of element.querySelectorAll<HTMLElement>("[data-api-token]")) span.textContent = inlineApiValue(span.dataset.apiToken!, latest.fields, latest.values) || "\u2060";
    if (Array.from(element.querySelectorAll<HTMLElement>("*")).some((node) => !node.dataset.apiToken && !node.hasAttribute("data-editor-sentinel"))) paint(next, at);
    if (next !== latest.value) latest.onCommit(next);
  }
  function insert(text: string) {
    const element = root.current, latest = current.current; if (!element || !latest.editable) return;
    const range = selectionRange(element); if (!range || touchesField(element, range)) return;
    const start = offsetAt(element, range.startContainer, range.startOffset), end = offsetAt(element, range.endContainer, range.endOffset);
    const plain = latest.multiline ? text.replace(/\r\n?/g, "\n") : text.replace(/[\r\n]+/g, " ");
    const next = latest.value.slice(0, start) + plain + latest.value.slice(end);
    if (next.length > latest.maxLength || !sameInlineApiFields(latest.value, next)) return;
    paint(next, start + plain.length); latest.onCommit(next);
  }
  useLayoutEffect(() => {
    if (composing.current) return;
    const element = root.current!;
    const range = selectionRange(element), caret = document.activeElement === element && range ? offsetAt(element, range.startContainer, range.startOffset) : undefined;
    if (sourceText(element) !== props.value) paint(props.value, caret);
    else for (const span of element.querySelectorAll<HTMLElement>("[data-api-token]")) {
      const text = inlineApiValue(span.dataset.apiToken!, props.fields, props.values) || "\u2060";
      if (span.textContent !== text) span.textContent = text;
    }
  }, [props.value, props.fields, props.values]);
  useLayoutEffect(() => {
    const element = root.current!;
    const beforeInput = (event: InputEvent) => {
      const range = selectionRange(element); if (!range) { event.preventDefault(); return; }
      const targets = event.getTargetRanges?.() || [];
      const touching = touchesField(element, range) || targets.some((target) => { const r = document.createRange(); r.setStart(target.startContainer, target.startOffset); r.setEnd(target.endContainer, target.endOffset); return touchesField(element, r); });
      if (touching || event.inputType.startsWith("format") || ["insertFromDrop", "deleteByDrag"].includes(event.inputType)) { event.preventDefault(); return; }
      if (["insertParagraph", "insertLineBreak"].includes(event.inputType)) { event.preventDefault(); if (current.current.multiline) insert("\n"); return; }
      if (range.collapsed && event.inputType.startsWith("delete")) {
        const at = offsetAt(element, range.startContainer, range.startOffset);
        if (inlineApiParts(current.current.value).some((part) => part.key && (event.inputType.includes("Backward") ? part.end === at : part.start === at))) event.preventDefault();
      }
    };
    element.addEventListener("beforeinput", beforeInput); return () => element.removeEventListener("beforeinput", beforeInput);
  }, []);
  function keyDown(event: KeyboardEvent<HTMLDivElement>) {
    if ((event.ctrlKey || event.metaKey) && ["b", "i", "u"].includes(event.key.toLowerCase())) event.preventDefault();
    if (event.key === "Enter") { event.preventDefault(); if (props.multiline) insert("\n"); else event.currentTarget.blur(); }
  }
  function paste(event: ClipboardEvent<HTMLDivElement>) { event.preventDefault(); insert(event.clipboardData.getData("text/plain")); }
  function cut(event: ClipboardEvent<HTMLDivElement>) {
    const range = root.current && selectionRange(root.current); event.preventDefault();
    if (range && !touchesField(root.current!, range)) { event.clipboardData.setData("text/plain", range.toString()); insert(""); }
  }
  return <div ref={root} role="textbox" aria-label={props.label} aria-multiline={props.multiline} aria-readonly={!props.editable} contentEditable={props.editable} suppressContentEditableWarning
    className={`pdf-editor-inline pdf-editor-protected-text ${props.className}`} spellCheck onFocus={props.onFocus} onInput={commitInput} onKeyDown={keyDown} onPaste={paste} onCut={cut}
    onCompositionStart={() => { composing.current = true; }} onCompositionEnd={() => { composing.current = false; commitInput(); }} onDragStart={(event) => event.preventDefault()} onDrop={(event) => event.preventDefault()} />;
}
