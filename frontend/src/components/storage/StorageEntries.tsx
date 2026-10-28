import type { StorageContents } from '../../api/items'
import { Button } from '../ui'
import { canDownload, type DocumentAccessPolicy } from '../../api/documentAccess'
import { useMemo, useState } from 'react'

const pageSize = 100
const processingLabels: Record<string, string> = {
  CONTENT_INDEXED: 'Content indexed', CONTENT_TRUNCATED: 'Content partially indexed',
  CONTENT_QUEUED: 'Content queued', CONTENT_PROCESSING: 'Processing content',
  CONTENT_RETRY: 'Content processing will retry', CONTENT_FAILED: 'Content processing failed',
  OCR_REQUIRED: 'OCR service required', INDEX_ERROR: 'Index error',
  CONTENT_UNAVAILABLE: 'Metadata indexed; content unavailable',
}

function formatSize(size?: number) {
  if (size == null) return '—'
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / (1024 * 1024)).toFixed(1)} MB`
}

export function StorageEntries({
  contents,
  parentPath,
  onNavigate,
  onDownload,
  downloading,
  policy,
  onOpenPdf,
  onSharePdf,
}: Readonly<{
  contents: StorageContents
  parentPath: string
  onNavigate: (path: string) => void
  onDownload: (path: string, archive: boolean, filename: string) => void
  downloading: boolean
  policy?: DocumentAccessPolicy
  onOpenPdf: (path: string) => void
  onSharePdf?: (path: string) => void
}>) {
  const [page, setPage] = useState(0)
  const pageCount = Math.max(1, Math.ceil(contents.entries.length / pageSize))
  const currentPage = Math.min(page, pageCount - 1)
  const visible = contents.entries.slice(currentPage * pageSize, (currentPage + 1) * pageSize)
  const folders = useMemo(() => contents.entries.reduce((count, entry) => count + Number(entry.type === 'FOLDER'), 0), [contents.entries])
  return (
    <div>
      <div role="status" style={{ padding: '0.5rem 1rem', color: 'var(--color-text-secondary)' }}>
        {folders} folders · {contents.entries.length - folders} files
      </div>
      {contents.path && (
        <button onClick={() => onNavigate(parentPath)} style={entryStyle}>

          <span style={{ flex: 1, textAlign: 'left' }}>..</span>
        </button>
      )}
      {visible.map(entry => {
        const vectorLabel = entry.vectorStatus ? ' · ' + (processingLabels[entry.vectorStatus] ?? 'Metadata indexed') : ''
        const detail = entry.type === 'FOLDER' ? 'Folder' : `${formatSize(entry.size)}${vectorLabel}`
        return (
          <div key={`${entry.type}-${entry.path}`} style={entryStyle}>

            <span style={{ flex: 1, minWidth: 0, textAlign: 'left', overflow: 'hidden', textOverflow: 'ellipsis' }}>{entry.name}</span>
            <span style={{ width: 90, textAlign: 'right', color: 'var(--color-text-tertiary)', fontSize: '0.75rem' }}>
              {detail}
            </span>
            {entry.type === 'OBJECT' && entry.name.toLowerCase().endsWith('.pdf') && onSharePdf && <Button variant="ghost" size="sm" onClick={() => onSharePdf(entry.path)}>Email PDF</Button>}
            {entry.type === 'FOLDER' && <Button variant="ghost" size="sm" onClick={() => onNavigate(entry.path)}>Open </Button>}
            {entry.type === 'OBJECT' && (entry.name.toLowerCase().endsWith('.pdf') || entry.contentType?.split(';')[0] === 'application/pdf') && policy?.pdfPreviewEnabled && <Button variant="ghost" size="sm" onClick={() => onOpenPdf(entry.path)}>Open PDF</Button>}
            {canDownload(policy) && <Button variant="ghost" size="sm" disabled={downloading} onClick={() => onDownload(entry.path, entry.type === 'FOLDER', entry.type === 'FOLDER' ? `${entry.name}.zip` : entry.name)} title={`Download ${entry.name}`}>{policy?.downloadMode === 'WATERMARKED' && (entry.type === 'FOLDER' || entry.name.toLowerCase().endsWith('.pdf') || entry.contentType?.split(';')[0] === 'application/pdf') ? 'Download with watermark' : 'Download'}</Button>}
          </div>
        )
      })}
      {pageCount > 1 && <nav aria-label="Directory pages" className="inline-actions" style={{ padding: '1rem' }}>
        <Button variant="secondary" size="sm" disabled={currentPage === 0} onClick={() => setPage(currentPage - 1)}>Previous</Button>
        <span>Page {currentPage + 1} of {pageCount}</span>
        <Button variant="secondary" size="sm" disabled={currentPage + 1 === pageCount} onClick={() => setPage(currentPage + 1)}>Next</Button>
      </nav>}
      {contents.entries.length === 0 && (
        <div style={{ textAlign: 'center', padding: '3rem', color: 'var(--color-text-secondary)' }}>This folder is empty.</div>
      )}
    </div>
  )
}

const entryStyle: React.CSSProperties = {
  width: '100%', display: 'flex', alignItems: 'center', gap: 10, padding: '0.8rem 1rem',
  background: 'transparent', color: 'var(--color-text)',
}
