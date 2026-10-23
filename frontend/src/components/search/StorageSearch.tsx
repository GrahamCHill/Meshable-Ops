import { useId, useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { aiApi } from '../../api/ai'
import { searchApi } from '../../api/search'
import { getErrorMessage } from '../admin/adminUtils'
import { Button } from '../ui'
import { SearchResult } from './SearchResult'

export function StorageSearch({ folderId, path = '', onOpenPdf }: { folderId?: string; path?: string; onOpenPdf?: (path: string) => void }) {
  const id = useId()
  const [query, setQuery] = useState('')
  const status = useQuery({ queryKey: ['ai-search-status'], queryFn: aiApi.status, refetchInterval: 30000 })
  const search = useMutation({ mutationFn: (text: string) => searchApi.search(text, 10, folderId, path) })
  if (!status.data?.searchEnabled) return null
  return <section className="page-stack storage-search" aria-label="Search storage">
    <form onSubmit={event => { event.preventDefault(); if (query.trim()) search.mutate(query.trim()) }}>
      <label htmlFor={id}>{folderId ? 'Search this folder and subfolders' : 'Search all shared storage'}</label>
      <div className="storage-search-form"><input id={id} type="search" maxLength={4000} placeholder="Search documents by meaning or keywords" value={query} onChange={event => setQuery(event.target.value)} /><Button type="submit" disabled={!query.trim()} loading={search.isPending}>Search</Button>{search.data && <Button type="button" variant="ghost" onClick={() => { search.reset(); setQuery('') }}>Clear</Button>}</div>
    </form>
    {search.isError && <p role="alert" className="feedback error">{getErrorMessage(search.error)}</p>}
    {search.data && <section className="page-stack" aria-label="Storage search results"><p role="status">{search.data.results.length} {search.data.results.length === 1 ? 'result' : 'results'} for “{search.variables}”</p>{search.data.notice && <p className="field-help">{search.data.notice}</p>}{search.data.results.map((result, index) => <SearchResult key={result.pointId} result={result} rank={index + 1} keyword={search.data.mode === 'KEYWORD'} onOpenPdf={onOpenPdf} />)}{!search.data.results.length && <p>No matches found. Try another query.</p>}</section>}
  </section>
}
