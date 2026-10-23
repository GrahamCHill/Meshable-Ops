import axios from 'axios'

const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL ?? '/api',
  headers: { 'Content-Type': 'application/json' },
})

// Inject JWT on every request
api.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

// Auto-logout on 401
api.interceptors.response.use(
  res => res,
  err => {
    if (err.response?.status === 401) {
      localStorage.removeItem('token')
      window.location.href = '/login'
    }
    return Promise.reject(err)
  }
)

export default api

// ─── Types ───────────────────────────────────────────────────────────────────

export type StorageProvider = 'NONE' | 'S3' | 'AZURE_BLOB'

export interface AuthResponse {
  token: string
  id: string
  email: string
  username: string
  name?: string
  contactNumber?: string
  role: string
  canUploadDocuments: boolean
}

export interface AdminUser {
  id: string
  email: string
  username: string
  name?: string
  contactNumber?: string
  role: string
  canUploadDocuments: boolean
  active: boolean
  createdAt: string
  updatedAt: string
}

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

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface Event {
  id: string
  type: string
  payload: Record<string, unknown>
  createdAt: string
}

export interface AuditLogEntry {
  id: string
  userId: string
  userEmail?: string
  userName?: string
  userContactNumber?: string
  action: string
  resourceType?: string
  resourceId?: string
  ipAddress?: string
  userAgent?: string
  timestamp: string
}

export interface VectorSearchResult {
  pointId: string
  score: number
  payload: Record<string, string>
}

export interface HealthStatus {
  timestamp: string
  postgres: string
  mongodb: string
  pgvector: string
}

export interface StackServiceInfo {
  name: string
  status: string
  version?: string | null
  runtime?: string | null
  internalUrl?: string | null
  exposedOutsideVm: boolean
  notes: string[]
}

export interface StackInfo {
  timestamp: string
  publicEntrypoints: string[]
  privateServices: string[]
  services: StackServiceInfo[]
}

export interface DashboardEmbedConfig {
  id: string
  label: string
  description?: string
  dashboardId: string
  guestTokenResourceId?: string
  sortOrder: number
  enabled: boolean
  allowedRoles: string[]
  createdAt: string
  updatedAt: string
}

export interface SupersetDashboardOption {
  id: number
  title: string
  published: boolean
  embeddingEnabled: boolean
}

export interface AdminSettings {
  id: string
  storageProvider: StorageProvider
  storageBucket?: string
  storageRegion?: string
  storageEndpoint?: string
  storageAccessKey?: string
  storageSecretKey?: string
  storageRootPath: string
  vectorReindexEnabled: boolean
  vectorReindexIntervalMinutes: number
  lastVectorReindexAt?: string
  lastVectorReindexStatus?: string
  createdAt: string
  updatedAt: string
}

// ─── API calls ───────────────────────────────────────────────────────────────

export const authApi = {
  register: (data: { email: string; username: string; password: string }) =>
    api.post<AuthResponse>('/auth/register', data).then(r => r.data),
  login: (data: { email: string; password: string }) =>
    api.post<AuthResponse>('/auth/login', data).then(r => r.data),
  refreshSessionCookie: () =>
    api.post<AuthResponse>('/auth/session-cookie').then(r => r.data),
  logout: () =>
    api.post('/auth/logout').then(r => r.data),
}

export const adminUsersApi = {
  list: () =>
    api.get<AdminUser[]>('/admin/users').then(r => r.data),
  create: (data: { email: string; username: string; name?: string; contactNumber?: string; password: string; role: 'ADMIN' | 'USER'; canUploadDocuments?: boolean }) =>
    api.post<AdminUser>('/admin/users', data).then(r => r.data),
  updateUploadPermission: (id: string, canUploadDocuments: boolean) =>
    api.patch<AdminUser>(`/admin/users/${id}/upload-permission`, { canUploadDocuments }).then(r => r.data),
  deactivate: (id: string) =>
    api.delete<AdminUser>(`/admin/users/${id}`).then(r => r.data),
}

export const itemsApi = {
  list: (page = 0, size = 20, connectionId?: string) =>
    api.get<PageResponse<Item>>('/items', { params: { page, size, connectionId } }).then(r => r.data),
  create: (data: { title: string; description?: string; tags?: string[] }) =>
    api.post<Item>('/items', data).then(r => r.data),
  update: (id: string, data: { title: string; description?: string; tags?: string[] }) =>
    api.put<Item>(`/items/${id}`, data).then(r => r.data),
  delete: (id: string) =>
    api.delete(`/items/${id}`),
  reindex: () =>
    api.post<{ message: string }>('/items/reindex').then(r => r.data),
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

export const eventsApi = {
  list: (page = 0, size = 20) =>
    api.get<PageResponse<Event>>('/events', { params: { page, size } }).then(r => r.data),
}

export const searchApi = {
  search: (query: string, limit = 10) =>
    api.post<VectorSearchResult[]>('/search', { query, limit }).then(r => r.data),
}

export const healthApi = {
  check: () => api.get<HealthStatus>('/health').then(r => r.data),
}

export const stackApi = {
  get: () => api.get<StackInfo>('/stack').then(r => r.data),
}

export const adminSettingsApi = {
  testStorage: (data: Pick<AdminSettings, 'storageProvider' | 'storageRootPath' | 'vectorReindexEnabled' | 'vectorReindexIntervalMinutes'> & Partial<Pick<AdminSettings, 'storageBucket' | 'storageRegion' | 'storageEndpoint' | 'storageAccessKey' | 'storageSecretKey'>>) =>
    api.post<{ message: string }>('/admin/settings/test-storage', data, { timeout: 30_000 }).then(r => r.data),
  get: () => api.get<AdminSettings>('/admin/settings').then(r => r.data),
  update: (data: {
    storageProvider: StorageProvider
    storageBucket?: string
    storageRegion?: string
    storageEndpoint?: string
    storageAccessKey?: string
    storageSecretKey?: string
    storageRootPath: string
    vectorReindexEnabled: boolean
    vectorReindexIntervalMinutes: number
  }) => api.put<AdminSettings>('/admin/settings', data).then(r => r.data),
}

export const adminStackApi = {
  get: () => api.get<StackInfo>('/admin/stack').then(r => r.data),
}

export const auditLogsApi = {
  list: (page = 0, size = 20) =>
    api.get<PageResponse<AuditLogEntry>>('/admin/audit-logs', { params: { page, size } }).then(r => r.data),
}

export const adminBackupApi = {
  export: () =>
    api.post<Blob>('/admin/backup', undefined, { responseType: 'blob' }).then(r => ({
      blob: r.data,
      filename: r.headers['content-disposition'] as string | undefined,
    })),
}

export const dashboardEmbedsApi = {
  list: () =>
    api.get<DashboardEmbedConfig[]>('/superset/dashboard-embeds').then(r => r.data),
  listAdmin: () =>
    api.get<DashboardEmbedConfig[]>('/superset/dashboard-embeds/admin').then(r => r.data),
  create: (data: {
    label: string
    description?: string
    supersetDashboardId: number
    sortOrder: number
    enabled: boolean
    allowedRoles: string[]
  }) => api.post<DashboardEmbedConfig>('/superset/dashboard-embeds', data).then(r => r.data),
  update: (id: string, data: {
    label: string
    description?: string
    supersetDashboardId: number
    sortOrder: number
    enabled: boolean
    allowedRoles: string[]
  }) => api.put<DashboardEmbedConfig>(`/superset/dashboard-embeds/${id}`, data).then(r => r.data),
  delete: (id: string) =>
    api.delete(`/superset/dashboard-embeds/${id}`),
  listRoles: () =>
    api.get<string[]>('/superset/dashboard-embeds/roles').then(r => r.data),
  listAvailable: () =>
    api.get<SupersetDashboardOption[]>('/superset/dashboards/available').then(r => r.data),
}
