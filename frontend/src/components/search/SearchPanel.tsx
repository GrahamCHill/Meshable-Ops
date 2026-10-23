import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useMutation, useQuery } from '@tanstack/react-query'
import { aiApi } from '../../api/ai'
import { searchApi } from '../../api/search'
import { useAuthStore } from '../../hooks/useAuthStore'
import { getErrorMessage } from '../admin/adminUtils'
import { AiAnswerPanel } from './AiAnswerPanel'
import { SearchResult } from './SearchResult'
import { Button, Spinner } from '../ui'

export function SearchPanel({ compact = false, onNavigate }: { compact?: boolean; onNavigate?: () => void }) {
  const [query, setQuery] = useState('')
  const [limit, setLimit] = useState(5)
  const admin = useAuthStore(state => state.user?.role === 'ADMIN')
  const status = useQuery({ queryKey: ['ai-search-status'], queryFn: aiApi.status, refetchInterval: 30000 })
  const search = useMutation({ mutationFn: (request: { query: string; limit: number }) => searchApi.search(request.query, request.limit) })
  const answer = useMutation({ mutationFn: (question: string) => aiApi.ask(question, Math.min(limit, 8)) })
  const busy = search.isPending || answer.isPending
  const askEnabled = status.data?.enabled && status.data.indexContent
  const runSearch = () => { if (query.trim()) { answer.reset(); search.mutate({ query: query.trim(), limit }) } }
  const ask = () => { if (query.trim()) { search.reset(); answer.mutate(query.trim()) } }
  const settingsLink = admin && <Link to="/admin/ai" onClick={onNavigate}>Search and AI settings</Link>

  if (status.data?.searchEnabled === false) return <div className="page-stack"><p>Search is disabled by an administrator.</p>{settingsLink}</div>
  return <div className="page-stack">
    {!compact && <div className="page-heading"><div><h1>Search & AI</h1><p>Search accessible folders and documents. AI answers are optional.</p></div>{settingsLink}</div>}
    <form className="page-stack" onSubmit={event => { event.preventDefault(); runSearch() }}>
      <label htmlFor={compact ? 'assistant-query' : 'storage-search-query'}>{askEnabled ? 'Search or ask about your documents' : 'Search your documents'}</label>
      <input id={compact ? 'assistant-query' : 'storage-search-query'} autoFocus={compact} placeholder={askEnabled ? 'Which contracts renew this quarter?' : 'Search folders and documents'} maxLength={4000} value={query} onChange={event => setQuery(event.target.value)} />
      <div className="inline-actions"><Button type="submit" disabled={!query.trim() || busy}>Search documents</Button>{askEnabled && <Button type="button" variant="secondary" disabled={!query.trim() || busy} onClick={ask}>Ask AI</Button>}<label htmlFor={compact ? 'assistant-limit' : 'search-limit'}>Results</label><select id={compact ? 'assistant-limit' : 'search-limit'} value={limit} onChange={event => setLimit(Number(event.target.value))} style={{ width: 72 }}>{[5, 10, 20, 50].map(value => <option key={value} value={value}>{value}</option>)}</select></div>
      {!askEnabled && <p className="field-help">AI answers are off or not configured. Search remains available.</p>}
      {status.data?.embeddingProvider === 'LOCAL' && <p className="field-help">Local text matching needs no AI provider or API key.</p>}
    </form>
    {busy && <p role="status"><Spinner /> {answer.isPending ? 'Finding excerpts and preparing an answer…' : 'Searching…'}</p>}
    {(search.isError || answer.isError) && <p role="alert" className="feedback error">{getErrorMessage(search.error ?? answer.error)}</p>}
    {answer.data && <><p className="field-help">Question: {answer.variables}</p><AiAnswerPanel answer={answer.data} onNavigate={onNavigate} /></>}
    {search.data && <section className="page-stack" aria-label="Search results"><p className="field-help">{search.data.results.length} results for “{search.variables?.query}”</p>{search.data.notice && <p role="status" className="field-help">{search.data.notice}</p>}{search.data.results.map((result, index) => <SearchResult key={result.pointId} result={result} rank={index + 1} keyword={search.data.mode === 'KEYWORD'} onNavigate={onNavigate} />)}{!search.data.results.length && <p>No matches found. Try another query or ask an administrator to reindex storage.</p>}</section>}
    {compact && settingsLink}
  </div>
}
