import api from './http'

export interface VectorSearchResult {
  pointId: string
  score: number
  payload: Record<string, string>
}

export interface SearchResponse { results: VectorSearchResult[]; mode: 'VECTOR' | 'KEYWORD'; notice?: string }

export const searchApi = {
  search: (query: string, limit = 10) =>
    api.post<SearchResponse>('/search', { query, limit }).then(r => r.data),
}
