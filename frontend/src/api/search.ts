import api from './http'

export interface VectorSearchResult {
  pointId: string
  score: number
  payload: Record<string, string>
}

export interface SearchResponse { results: VectorSearchResult[]; mode: 'VECTOR' | 'KEYWORD' | 'MIXED'; notice?: string }

export const searchApi = {
  search: (query: string, limit = 10, folderId?: string, path = '') =>
    api.post<SearchResponse>('/search', { query, limit, folderId, path }).then(r => r.data),
}
