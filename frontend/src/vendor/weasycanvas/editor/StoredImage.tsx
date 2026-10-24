"use client";

import { createContext, useContext, useEffect, useMemo, useRef, useState, type ImgHTMLAttributes, type ReactNode } from "react";
import { createHttpImageStorage, isImageSource, validateImageBlob, type PdfEditorImageSource, type PdfEditorImageStorage } from "./imageStorage";

const defaultStorage = createHttpImageStorage();
function createCache(storage: PdfEditorImageStorage) {
  const pending = new Map<string, Promise<string>>(), urls = new Set<string>(), requests = new Set<AbortController>();
  let disposed = false;
  return { storage,
    load(key: string) {
      disposed = false;
      const cached = pending.get(key); if (cached) return cached;
      const controller = new AbortController(); requests.add(controller);
      const promise: Promise<string> = storage.load(key, controller.signal).then(validateImageBlob).then(blob => {
        if (disposed || controller.signal.aborted) throw new Error("Image loading cancelled.");
        const url = URL.createObjectURL(blob); urls.add(url); return url;
      }).catch(error => { if (pending.get(key) === promise) pending.delete(key); throw error; }).finally(() => requests.delete(controller));
      pending.set(key, promise); return promise;
    },
    dispose() { disposed = true; requests.forEach(request => request.abort()); urls.forEach(url => URL.revokeObjectURL(url)); requests.clear(); urls.clear(); pending.clear(); },
  };
}
const Context = createContext<ReturnType<typeof createCache> | null>(null);
/** One cache per integration/provider; repeating headers and measurement share each download. */
export function ImageStorageProvider({ storage = defaultStorage, children }: { storage?: PdfEditorImageStorage; children: ReactNode }) {
  const cache = useMemo(() => createCache(storage), [storage]);
  useEffect(() => () => cache.dispose(), [cache]);
  return <Context.Provider value={cache}>{children}</Context.Provider>;
}
export const useImageStorage = () => useContext(Context)?.storage || defaultStorage;
export type StoredImageProps = Omit<ImgHTMLAttributes<HTMLImageElement>, "src"> & { source: PdfEditorImageSource; onSourceError?: (error: Error) => void };
export function StoredImage({ source, onSourceError, onError, ...props }: StoredImageProps) {
  const inherited = useContext(Context), local = useMemo(() => inherited ? null : createCache(defaultStorage), [inherited]);
  const cache = inherited || local!;
  const callback = useRef(onSourceError); callback.current = onSourceError;
  const valid = isImageSource(source), key = valid ? source.assetKey : undefined;
  const [loaded, setLoaded] = useState<{ cache: typeof cache; key: string; url: string } | null>(null);
  useEffect(() => {
    if (!valid) { callback.current?.(new Error("Supply one valid embedded image or storage key.")); return; }
    if (!key) return;
    let active = true;
    void cache.load(key).then(url => { if (active) setLoaded({ cache, key, url }); }).catch(error => { if (active) callback.current?.(error instanceof Error ? error : new Error("Could not load image.")); });
    return () => { active = false; };
  }, [key, cache, valid]);
  useEffect(() => () => local?.dispose(), [local]);
  return <img {...props} src={valid ? source.dataUrl || (loaded && loaded.key === key && loaded.cache === cache ? loaded.url : undefined) : undefined} onError={event => { onError?.(event); callback.current?.(new Error("The image could not be decoded.")); }} />;
}
