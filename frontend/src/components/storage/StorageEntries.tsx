import type { StorageContents } from '../../api/items'
import { Button } from '../ui'
import { canDownload, type DocumentAccessPolicy } from '../../api/documentAccess'

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
}: Readonly<{
  contents: StorageContents
  parentPath: string
  onNavigate: (path: string) => void
  onDownload: (path: string, archive: boolean, filename: string) => void
  downloading: boolean
  policy?: DocumentAccessPolicy
  onOpenPdf: (path: string) => void
}>) {
  return (
    <div>
      {contents.path && (
        <button onClick={() => onNavigate(parentPath)} style={entryStyle}>

          <span style={{ flex: 1, textAlign: 'left' }}>..</span>
        </button>
      )}
      {contents.entries.map(entry => {
        const vectorLabel = entry.vectorStatus === 'CONTENT_INDEXED' ? ' · Content indexed' : entry.vectorStatus === 'CONTENT_TRUNCATED' ? ' · Content partially indexed' : entry.vectorStatus === 'INDEX_ERROR' ? ' · Index error' : entry.vectorStatus === 'CONTENT_UNAVAILABLE' ? ' · Metadata indexed; content unavailable' : entry.vectorStatus ? ' · Metadata indexed' : ''
        const detail = entry.type === 'FOLDER' ? 'Folder' : `${formatSize(entry.size)}${vectorLabel}`
        return (
          <div key={`${entry.type}-${entry.path}`} style={entryStyle}>

            <span style={{ flex: 1, minWidth: 0, textAlign: 'left', overflow: 'hidden', textOverflow: 'ellipsis' }}>{entry.name}</span>
            <span style={{ width: 90, textAlign: 'right', color: 'var(--color-text-tertiary)', fontSize: '0.75rem' }}>
              {detail}
            </span>
            {entry.type === 'FOLDER' && <Button variant="ghost" size="sm" onClick={() => onNavigate(entry.path)}>Open </Button>}
            {entry.type === 'OBJECT' && (entry.name.toLowerCase().endsWith('.pdf') || entry.contentType?.split(';')[0] === 'application/pdf') && policy?.pdfPreviewEnabled && <Button variant="ghost" size="sm" onClick={() => onOpenPdf(entry.path)}>Open PDF</Button>}
            {canDownload(policy) && <Button variant="ghost" size="sm" disabled={downloading} onClick={() => onDownload(entry.path, entry.type === 'FOLDER', entry.type === 'FOLDER' ? `${entry.name}.zip` : entry.name)} title={`Download ${entry.name}`}>{policy?.downloadMode === 'WATERMARKED' && (entry.type === 'FOLDER' || entry.name.toLowerCase().endsWith('.pdf') || entry.contentType?.split(';')[0] === 'application/pdf') ? 'Download with watermark' : 'Download'}</Button>}
          </div>
        )
      })}
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
