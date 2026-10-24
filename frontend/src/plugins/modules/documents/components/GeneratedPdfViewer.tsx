import { useEffect, useRef, useState } from 'react'
import { documentsApi, type GeneratedDocument } from '../api'
import { Button } from '../../../../components/ui/index'

export function GeneratedPdfViewer({ document, onClose, loadPage }: { document: GeneratedDocument; onClose: () => void; loadPage?: (page: number, signal: AbortSignal) => Promise<Blob> }) {
  const dialog = useRef<HTMLDialogElement>(null)
  const [page, setPage] = useState(1); const [url, setUrl] = useState(''); const [error, setError] = useState('')
  useEffect(() => { dialog.current?.showModal() }, [])
  useEffect(() => {
    const controller = new AbortController(); let objectUrl = ''; setUrl(''); setError('')
    void (loadPage ? loadPage(page, controller.signal) : documentsApi.page(document.id, page, controller.signal)).then(blob => { if (!controller.signal.aborted) { objectUrl = URL.createObjectURL(blob); setUrl(objectUrl) } }).catch(() => { if (!controller.signal.aborted) setError('Unable to open this PDF page.') })
    return () => { controller.abort(); if (objectUrl) URL.revokeObjectURL(objectUrl) }
  }, [document.id, page, loadPage])
  return <dialog ref={dialog} className="pdf-viewer" aria-labelledby="generated-pdf-title" onCancel={e => { e.preventDefault(); onClose() }}><div className="pdf-toolbar"><h2 id="generated-pdf-title">{document.name}</h2><Button variant="ghost" onClick={onClose}>Close PDF</Button></div><nav className="inline-actions" aria-label="PDF pages"><Button variant="ghost" disabled={page <= 1} onClick={() => setPage(page - 1)}>Previous page</Button><span>Page {page} of {document.pageCount}</span><Button variant="ghost" disabled={page >= document.pageCount} onClick={() => setPage(page + 1)}>Next page</Button></nav>{error && <p role="alert">{error}</p>}<div className="pdf-canvas">{url ? <img className="pdf-page" src={url} alt={`Document page ${page}`} /> : !error && <p>Opening page…</p>}</div></dialog>
}
