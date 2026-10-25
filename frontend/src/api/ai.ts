import api from './http'

export type AiProvider = 'OPENAI_COMPATIBLE' | 'OLLAMA' | 'AZURE_OPENAI' | 'ANTHROPIC' | 'LOCAL'
export interface AiEndpointView { provider: AiProvider; baseUrl: string; model: string; apiKeyConfigured: boolean; sendDimensions: boolean }
export interface AiConfigurationView { enabled: boolean; indexContent: boolean; searchEnabled: boolean; launcherEnabled: boolean; chat: AiEndpointView; embeddings: AiEndpointView }
export interface AiSettings { source: 'ENVIRONMENT' | 'ADMIN'; encryptionReady: boolean; admin: AiConfigurationView; environment: AiConfigurationView }
export interface AiEndpointInput { provider: AiProvider; baseUrl: string; model: string; apiKey: string; clearApiKey: boolean; sendDimensions: boolean }
export interface AiSettingsInput { source: AiSettings['source']; enabled: boolean; indexContent: boolean; searchEnabled: boolean; launcherEnabled: boolean; chat: AiEndpointInput; embeddings: AiEndpointInput }
export interface AiSearchStatus { enabled: boolean; indexContent: boolean; searchEnabled: boolean; launcherEnabled: boolean; embeddingProvider: AiProvider; dimensions: number }
export interface AiSource { number: number; pointId: string; documentId: string; folderId: string; title: string; path: string; excerpt: string; score: number }
export interface AiAnswer { answer: string; sources: AiSource[]; model?: string; grounded: boolean }
export interface AiEndpointCheckInput { source: AiSettings['source']; endpoint: AiEndpointInput }
export interface AiModels { models: { id: string; name: string }[]; message: string; truncated: boolean }
export interface AiModelCheck { status: 'COMPATIBLE' | 'SETTING_CHANGE_REQUIRED' | 'INCOMPATIBLE'; model: string; message: string; requiredDimensions?: number; actualDimensions?: number; suggestedSendDimensions?: boolean }

export const aiApi = {
  settings: () => api.get<AiSettings>('/admin/ai').then(response => response.data),
  save: (input: AiSettingsInput) => api.put<AiSettings>('/admin/ai', input).then(response => response.data),
  models: (kind: 'chat' | 'embeddings', input: AiEndpointCheckInput, signal?: AbortSignal) => api.post<AiModels>(`/admin/ai/models/${kind}`, input, { timeout: 60000, signal }).then(response => response.data),
  check: (kind: 'chat' | 'embeddings', input: AiEndpointCheckInput, signal?: AbortSignal) => api.post<AiModelCheck>(`/admin/ai/check/${kind}`, input, { timeout: 120000, signal }).then(response => response.data),
  test: (kind: 'chat' | 'embeddings') => api.post<{ message: string }>(`/admin/ai/test/${kind}`, undefined, { timeout: 60000 }).then(response => response.data),
  status: () => api.get<AiSearchStatus>('/search/status').then(response => response.data),
  ask: (query: string, limit = 5) => api.post<AiAnswer>('/search/ask', { query, limit }, { timeout: 90000 }).then(response => response.data),
}

export function endpointInput(view: AiEndpointView): AiEndpointInput {
  return { provider: view.provider, baseUrl: view.baseUrl, model: view.model, apiKey: '', clearApiKey: false, sendDimensions: view.sendDimensions }
}
