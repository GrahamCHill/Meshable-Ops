import api from './http'
import type { StorageProvider, PageResponse } from './types'

export interface Item {
  id: string
  title: string
  description?: string
  tags?: string[]
  storagePrefix: string
  storageProvider: StorageProvider
  documentCount: number
  createdAt: string
  updatedAt: string
  connectionId?: string
  connectionNickname?: string
  accessible: boolean
}

export interface StorageEntry {
  name: string
  path: string
  type: 'FOLDER' | 'OBJECT'
  size?: number
  contentType?: string
  lastModified?: string
  documentId?: string
  vectorStatus?: string
}

export interface StorageContents {
  folderId: string
  rootPrefix: string
  path: string
  storageProvider: StorageProvider
  entries: StorageEntry[]
}

export const itemsApi = {
  get: (id: string) => api.get<Item>(`/items/${id}`).then(response => response.data),
  list: (page = 0, size = 20, connectionId?: string) =>
    api.get<PageResponse<Item>>('/items', { params: { page, size, connectionId } }).then(r => r.data),
  create: (data: { title: string; description?: string; tags?: string[] }) =>
    api.post<Item>('/items', data).then(r => r.data),
  update: (id: string, data: { title: string; description?: string; tags?: string[] }) =>
    api.put<Item>(`/items/${id}`, data).then(r => r.data),
  delete: (id: string) =>
    api.delete(`/items/${id}`),
  reindex: () =>
    api.post<{ message: string }>('/items/reindex', undefined, { timeout: 600000 }).then(r => r.data),
  contents: (id: string, path = '') =>
    api.get<StorageContents>(`/items/${id}/contents`, { params: { path } }).then(r => r.data),
  upload: (id: string, path: string, files: File[]) => {
    const form = new FormData()
    form.append('path', path)
    files.forEach(file => {
      form.append('files', file)
      form.append('paths', file.webkitRelativePath || file.name)
    })
    return api.post<StorageContents>(`/items/${id}/upload`, form, { headers: { 'Content-Type': 'multipart/form-data' } }).then(r => r.data)
  },
  download: (id: string, path: string, archive = false) =>
    api.get<Blob>(`/items/${id}/download`, { params: { path, archive }, responseType: 'blob' }).then(r => r.data),
}
