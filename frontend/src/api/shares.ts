import axios from 'axios'
import api from './http'

export type ShareSettings = { enabled: boolean; publicBaseUrl: string; maxLifetimeHours: number; emailAvailable: boolean }
export type FileShare = { id: string; filename: string; recipient: string; createdAt: string; expiresAt: string; revoked: boolean; deliveryStatus: string }
export type SharedFile = { filename: string; pageCount: number; recipient: string; expiresAt: string; canPreview: boolean; canDownload: boolean }
// Public links use a short-lived grant held in component memory, separate from account sessions.
const publicApi = axios.create({ baseURL: import.meta.env.VITE_API_URL ?? '/api', headers: { 'Content-Type': 'application/json' } })
const access = (grant: string) => ({ headers: { 'X-Share-Access': grant } })
export const sharesApi = {
  settings: () => api.get<ShareSettings>('/admin/shares/settings').then(r => r.data),
  saveSettings: (body: Omit<ShareSettings, 'emailAvailable'>) => api.put<ShareSettings>('/admin/shares/settings', body).then(r => r.data),
  list: () => api.get<FileShare[]>('/admin/shares').then(r => r.data),
  create: (body: { folderId: string; path: string; recipient: string; password: string; lifetimeHours: number }) => api.post<{ share: FileShare; url: string }>('/admin/shares', body).then(r => r.data),
  revoke: (id: string) => api.post(`/admin/shares/${id}/revoke`),
  unlock: (token: string, password: string) => publicApi.post<{ accessToken: string; expiresAt: string }>(`/shared/${encodeURIComponent(token)}/unlock`, { password }).then(r => r.data),
  metadata: (token: string, grant: string) => publicApi.get<SharedFile>(`/shared/${encodeURIComponent(token)}`, access(grant)).then(r => r.data),
  page: (token: string, grant: string, page: number, signal: AbortSignal) => publicApi.get<Blob>(`/shared/${encodeURIComponent(token)}/pages/${page}`, { ...access(grant), responseType: 'blob', signal }).then(r => r.data),
  download: (token: string, grant: string, password: string) => publicApi.post<Blob>(`/shared/${encodeURIComponent(token)}/download`, { password }, { ...access(grant), responseType: 'blob' }).then(r => r.data),
}
