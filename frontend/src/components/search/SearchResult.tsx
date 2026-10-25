import { Link } from 'react-router-dom'
import type { VectorSearchResult } from '../../api/search'
import { Card } from '../ui'
import { storageLink } from './storageLink'

export function SearchResult({ result, rank, keyword = false, onNavigate, onOpenPdf }: { result: VectorSearchResult; rank: number; keyword?: boolean; onNavigate?: () => void; onOpenPdf?: (path: string) => void }) {
  const payload = result.payload
  return <Card>
    <div className="page-heading">
      <div><h2 className="card-title">{rank}. {payload.title ?? result.pointId}</h2><p>{payload.entityType === 'plugin' ? payload.source : payload.entityType === 'chunk' ? 'Document excerpt' : payload.entityType === 'document' ? 'Document metadata' : 'Folder'}{payload.pageNumber ? ' · Page ' + payload.pageNumber : ''}{payload.storagePath ? ` · ${payload.storagePath}` : ''}</p></div>
      <span className="status-label">{payload.matchMode ?? (keyword ? 'Keyword match' : `Similarity ${result.score.toFixed(3)}`)}</span>
    </div>
    {payload.text && <p style={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere', margin: '12px 0', fontSize: '0.85rem' }}>{payload.text}</p>}
    {payload.folderId && <Link to={storageLink(payload.folderId, payload.storagePath)} onClick={onNavigate}>Open in Storage</Link>}
    {payload.entityType === 'plugin' && payload.href?.startsWith(`/plugins/${payload.pluginId}/`) && <Link to={payload.href} onClick={onNavigate}>Open in {payload.source ?? 'plugin'}</Link>}
    {onOpenPdf && payload.storagePath?.toLowerCase().endsWith('.pdf') && <button className="utility-button" style={{ marginLeft: 12 }} onClick={() => onOpenPdf(payload.storagePath)}>Open PDF</button>}
  </Card>
}
