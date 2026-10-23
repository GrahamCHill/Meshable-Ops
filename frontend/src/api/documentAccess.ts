import { useQuery } from '@tanstack/react-query'
import api from './http'

export interface DocumentAccessPolicy {
  pdfPreviewEnabled: boolean
  downloadMode: 'NORMAL' | 'WATERMARKED' | 'DISABLED'
  watermarkText: string
}

export const documentAccessApi = {
  policy: () => api.get<DocumentAccessPolicy>('/document-access').then(r => r.data),
  settings: () => api.get<DocumentAccessPolicy>('/admin/document-access').then(r => r.data),
  save: (policy: DocumentAccessPolicy) => api.put<DocumentAccessPolicy>('/admin/document-access', policy).then(r => r.data),
  preview: (id: string, path: string) => api.get<{ filename: string; pageCount: number }>(`/items/${id}/pdf`, { params: { path } }).then(r => r.data),
  page: (id: string, path: string, page: number, signal?: AbortSignal) => api.get<Blob>(`/items/${id}/pdf/pages/${page}`, { params: { path }, responseType: 'blob', signal }).then(r => r.data),
}

export function useDocumentAccess() {
  return useQuery({ queryKey: ['document-access'], queryFn: documentAccessApi.policy, refetchInterval: 30000 })
}

export function canDownload(policy: DocumentAccessPolicy | undefined) {
  return !!policy && policy.downloadMode !== 'DISABLED'
}
