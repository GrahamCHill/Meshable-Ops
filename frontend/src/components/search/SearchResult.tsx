import { Link } from 'react-router-dom'
import type { VectorSearchResult } from '../../api/search'
import { Card } from '../ui'
import { storageLink } from './storageLink'

export function SearchResult({ result, rank, keyword = false, onNavigate }: { result: VectorSearchResult; rank: number; keyword?: boolean; onNavigate?: () => void }) {
  const payload = result.payload
  return <Card>
    <div className="page-heading">
      <div><h2 className="card-title">{rank}. {payload.title ?? result.pointId}</h2><p>{payload.entityType === 'chunk' ? 'Document excerpt' : payload.entityType === 'document' ? 'Document metadata' : 'Folder'}{payload.storagePath ? ` · ${payload.storagePath}` : ''}</p></div>
      <span className="status-label">{keyword ? 'Keyword match' : `Similarity ${result.score.toFixed(3)}`}</span>
    </div>
    {payload.text && <p style={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere', margin: '12px 0', fontSize: '0.85rem' }}>{payload.text}</p>}
    {payload.folderId && <Link to={storageLink(payload.folderId, payload.storagePath)} onClick={onNavigate}>Open in Storage</Link>}
  </Card>
}
