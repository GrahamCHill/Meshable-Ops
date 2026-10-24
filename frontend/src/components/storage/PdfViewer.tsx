import {useEffect,useState} from 'react'
import {usePageDraft} from '../../hooks/usePageDraft'
import {useModalDialog} from '../../hooks/useModalDialog'
import { useQuery } from '@tanstack/react-query'
import { canDownload, documentAccessApi, type DocumentAccessPolicy } from '../../api/documentAccess'
import { getErrorMessage } from '../admin/adminUtils'
import { Button, Spinner } from '../ui'

function PdfPage({ folderId, path, page }: { folderId: string; path: string; page: number }) {
  const [url, setUrl] = useState('')
  const image = useQuery({ queryKey: ['pdf-page', folderId, path, page], queryFn: ({ signal }) => documentAccessApi.page(folderId, path, page, signal), gcTime: 0, staleTime: 0, retry: false })
  useEffect(() => {
    if (!image.data) return
    const objectUrl = URL.createObjectURL(image.data)
    setUrl(objectUrl)
    return () => URL.revokeObjectURL(objectUrl)
  }, [image.data])
  if (image.isError) return <p role="alert">This page could not be opened. Check PDF access settings or try another document.</p>
  return url ? <img src={url} alt={`PDF page ${page}`} className="pdf-page" /> : <p role="status"><Spinner /> Opening page…</p>
}

export function PdfViewer({ folderId, path, policy, onClose, onDownload, downloading }: {
  folderId: string; path: string; policy: DocumentAccessPolicy; onClose: () => void
  onDownload: (path: string, archive: boolean, filename: string) => void; downloading: boolean
}) {
  const dialog = useModalDialog(onClose)
  const [page, setPage] = usePageDraft('pdf-page:'+folderId+':'+path, 1)
  const preview = useQuery({ queryKey: ['pdf-preview', folderId, path], queryFn: () => documentAccessApi.preview(folderId, path), gcTime: 0, staleTime: 0, retry: false })
  return <dialog ref={dialog} className="pdf-viewer" aria-labelledby="pdf-title" onCancel={event => { event.preventDefault(); onClose() }}>
    <div className="pdf-toolbar"><h2 id="pdf-title">{preview.data?.filename ?? path.split('/').pop()}</h2><div className="inline-actions">
      {canDownload(policy) && <Button variant="secondary" size="sm" disabled={downloading} onClick={() => onDownload(path, false, preview.data?.filename ?? path.split('/').pop()!)}>{policy.downloadMode === 'WATERMARKED' ? 'Download with watermark' : 'Download'}</Button>}
      <Button variant="ghost" size="sm" autoFocus onClick={onClose}>Close PDF</Button>
    </div></div>
    {preview.isLoading && <p role="status"><Spinner /> Opening PDF…</p>}
    {preview.isError && <p role="alert">{getErrorMessage(preview.error, 'This PDF could not be opened.')}</p>}
    {preview.data && <><nav className="inline-actions pdf-pagination" aria-label="PDF pages"><Button variant="ghost" size="sm" disabled={page === 1} onClick={() => setPage(value => value - 1)}>Previous page</Button><span aria-live="polite">Page {page} of {preview.data.pageCount}</span><Button variant="ghost" size="sm" disabled={page === preview.data.pageCount} onClick={() => setPage(value => value + 1)}>Next page</Button></nav><div className="pdf-canvas"><PdfPage key={`${page}-${policy.downloadMode}-${policy.watermarkText}`} folderId={folderId} path={path} page={page} /></div></>}
  </dialog>
}
