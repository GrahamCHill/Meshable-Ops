import api from './http'

export interface HealthStatus {
  timestamp: string
  postgres: string
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

export const healthApi = {
  check: () => api.get<HealthStatus>('/health').then(r => r.data),
}

export const stackApi = {
  get: () => api.get<StackInfo>('/stack').then(r => r.data),
}

export const adminStackApi = {
  get: () => api.get<StackInfo>('/admin/stack').then(r => r.data),
}
