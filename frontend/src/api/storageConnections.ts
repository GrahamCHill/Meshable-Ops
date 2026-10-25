import api from './http'
import { type StorageProvider } from './types'
import { type StorageEntry } from './items'

export type SharedFolder = { id: string; nickname: string; path: string; accessible: boolean }
export type SharedConnection = { id: string; nickname: string; provider: StorageProvider; folderCount: number }
export type StorageConnectionInput = {
  nickname: string; provider: Exclude<StorageProvider, 'NONE'>; bucket: string; region: string; endpoint: string
  accessKey: string; secretKey: string; useServerIdentity: boolean; basePath: string; enabled: boolean
}
export type StorageConnection = Omit<StorageConnectionInput, 'accessKey' | 'secretKey' | 'useServerIdentity'> & {
  id: string; accessKeyConfigured: boolean; secretConfigured: boolean; folders: SharedFolder[]
}
export const storageConnectionsApi = {
  list: () => api.get<SharedConnection[]>('/storage-connections').then(response => response.data),
  listAdmin: () => api.get<StorageConnection[]>('/admin/storage-connections').then(response => response.data),
  save: ({ id, data }: { id?: string; data: StorageConnectionInput }) => (id ? api.put<StorageConnection>(`/admin/storage-connections/${id}`, data) : api.post<StorageConnection>('/admin/storage-connections', data)).then(response => response.data),
  remove: (id: string) => api.delete(`/admin/storage-connections/${id}`),
  test: (id: string) => api.post<{ message: string }>(`/admin/storage-connections/${id}/test`, undefined, { timeout: 30_000 }).then(response => response.data),
  browse: (id: string, path: string) => api.get<{ path: string; entries: StorageEntry[] }>(`/admin/storage-connections/${id}/browse`, { params: { path } }).then(response => response.data),
  share: ({ id, nickname, path }: { id: string; nickname: string; path: string }) => api.post<SharedFolder>(`/admin/storage-connections/${id}/folders`, { nickname, path, accessible: true }).then(response => response.data),
  legacyFolders: () => api.get<SharedFolder[]>('/admin/storage-folders').then(response => response.data),
  updateFolder: ({ id, nickname, accessible }: { id: string; nickname: string; accessible: boolean }) => api.patch<SharedFolder>(`/admin/storage-folders/${id}`, { nickname, accessible }).then(response => response.data),
  removeFolder: (id: string) => api.delete(`/admin/storage-folders/${id}`),
}
