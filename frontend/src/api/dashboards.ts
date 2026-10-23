import api from './http'

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
