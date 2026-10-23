import api from './http'
import type { StorageProvider } from './types'

export interface AdminSettings {
  id: string
  storageProvider: StorageProvider
  storageBucket?: string
  storageRegion?: string
  storageEndpoint?: string
  storageAccessKeyConfigured: boolean
  storageSecretKeyConfigured: boolean
  encryptionReady: boolean
  storageRootPath: string
  vectorReindexEnabled: boolean
  vectorReindexIntervalMinutes: number
  vectorReindexSchedule: 'INTERVAL' | 'DAILY' | 'WEEKLY' | 'ON_CHANGE'
  vectorReindexTime: string
  vectorReindexDayOfWeek: string
  vectorReindexTimeZone: string
  vectorReindexNextRunAt?: string
  vectorReindexPendingChanges: boolean
  vectorReindexLastCheckAt?: string
  lastVectorReindexAt?: string
  lastVectorReindexStatus?: string
  createdAt: string
  updatedAt: string
}

export type ReindexScheduleInput = Pick<AdminSettings, 'vectorReindexEnabled' | 'vectorReindexIntervalMinutes' | 'vectorReindexSchedule' | 'vectorReindexTime' | 'vectorReindexDayOfWeek' | 'vectorReindexTimeZone'>
export type AdminSettingsInput = Pick<AdminSettings, 'storageProvider' | 'storageRootPath'> & ReindexScheduleInput & {
  storageBucket?: string
  storageRegion?: string
  storageEndpoint?: string
  storageAccessKey?: string
  storageSecretKey?: string
  useServerIdentity: boolean
}

export const adminSettingsApi = {
  testStorage: (data: AdminSettingsInput) =>
    api.post<{ message: string }>('/admin/settings/test-storage', data, { timeout: 30_000 }).then(r => r.data),
  get: () => api.get<AdminSettings>('/admin/settings').then(r => r.data),
  update: (data: AdminSettingsInput) => api.put<AdminSettings>('/admin/settings', data).then(r => r.data),
}
