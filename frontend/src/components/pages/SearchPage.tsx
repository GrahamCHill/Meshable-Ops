import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { itemsApi, searchApi, type VectorSearchResult } from '../../api/client'
import { Button, Card, Spinner } from '../ui'
import { RefreshCcw, Search, Zap } from 'lucide-react'

export function SearchPage() {
  const [query, setQuery] = useState('')
  const [limit, setLimit] = useState(10)

  const search = useMutation({
    mutationFn: (q: string) => searchApi.search(q, limit),
  })

  const reindex = useMutation({
    mutationFn: () => itemsApi.reindex(),
  })

  const run = () => { if (query.trim()) search.mutate(query.trim()) }

  return (
    <div style={{ maxWidth: 720 }}>
      <div style={{ marginBottom: '1.5rem' }}>
        <h1 style={{ fontSize: '1.4rem', fontWeight: 700, marginBottom: 4 }}>Vector Search</h1>
        <p style={{ color: 'var(--color-text-secondary)', fontSize: '0.875rem' }}>
          Semantic similarity search via Qdrant and the internal ML embedding service
        </p>
      </div>

      <Card style={{ marginBottom: '1.5rem' }}>
        <div style={{ display: 'flex', gap: 8, marginBottom: 12 }}>
          <input
            placeholder="Enter search query..."
            value={query}
            onChange={e => setQuery(e.target.value)}
            onKeyDown={e => e.key === 'Enter' && run()}
            style={{ flex: 1 }}
          />
          <select value={limit} onChange={e => setLimit(Number(e.target.value))} style={{ width: 80 }}>
            {[5, 10, 20, 50].map(n => <option key={n} value={n}>{n}</option>)}
          </select>
          <Button onClick={run} loading={search.isPending} style={{ flexShrink: 0 }}>
            <Search size={14} /> Search
          </Button>
          <Button onClick={() => reindex.mutate()} loading={reindex.isPending} variant="secondary" style={{ flexShrink: 0 }}>
            <RefreshCcw size={14} /> Reindex Items
          </Button>
        </div>
        <p style={{ fontSize: '0.8rem', color: 'var(--color-text-tertiary)' }}>
          Search results show similarity, not exact-match confidence. Reindex existing items after embedding changes so stored vectors stay in sync.
        </p>
        {reindex.data && (
          <p style={{ fontSize: '0.8rem', color: 'var(--color-success)', marginTop: 8 }}>
            {reindex.data.message}
          </p>
        )}
      </Card>

      {search.isPending && <div style={{ display: 'flex', justifyContent: 'center', padding: '2rem' }}><Spinner /></div>}

      {search.data && (
        <div>
          <div style={{ fontSize: '0.8rem', color: 'var(--color-text-tertiary)', marginBottom: '0.75rem' }}>
            {search.data.length} results for "{query}"
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
            {search.data.map((r, i) => <SearchResult key={r.pointId} result={r} rank={i + 1} />)}
          </div>
          {search.data.length === 0 && (
            <Card style={{ textAlign: 'center', padding: '2rem', color: 'var(--color-text-secondary)' }}>
              No results found. Create some items first.
            </Card>
          )}
        </div>
      )}
    </div>
  )
}

function SearchResult({ result, rank }: { result: VectorSearchResult; rank: number }) {
  const score = result.score.toFixed(3)
  return (
    <Card>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <div style={{ display: 'flex', gap: 10, alignItems: 'flex-start' }}>
          <span style={{ width: 20, height: 20, borderRadius: '50%', background: 'var(--color-surface-2)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '0.7rem', color: 'var(--color-text-tertiary)', flexShrink: 0, marginTop: 2 }}>{rank}</span>
          <div>
            <div style={{ fontWeight: 600, fontSize: '0.9rem', marginBottom: 2 }}>{result.payload.title ?? result.pointId}</div>
            <div style={{ fontSize: '0.75rem', color: 'var(--color-text-tertiary)' }}>ID: {result.pointId.substring(0, 16)}...</div>
          </div>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 4, flexShrink: 0 }}>
          <Zap size={12} color="var(--color-accent)" />
          <span style={{ fontVariantNumeric: 'tabular-nums', fontSize: '0.875rem', fontWeight: 600, color: result.score > 0.7 ? 'var(--color-success)' : result.score > 0.4 ? 'var(--color-warning)' : 'var(--color-text-secondary)' }}>
            sim {score}
          </span>
        </div>
      </div>
    </Card>
  )
}
