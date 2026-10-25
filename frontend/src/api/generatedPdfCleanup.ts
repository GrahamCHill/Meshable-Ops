import api from './http'

export interface PdfCleanupInput {
  schedule: 'NEVER' | 'DAILY' | 'WEEKLY'
  retentionDays: number
  destination: 'AUTO' | 'DATABASE' | 'DEFAULT_STORAGE' | 'CONNECTION' | 'GOOGLE_DRIVE' | 'ONEDRIVE' | 'SHAREPOINT'
  connectionId?: string | null
}
export interface PdfCleanupCounts { reports: number; documents: number; bytes: number }
export interface PdfCleanupView {
  settings: PdfCleanupInput & {
    nextRunAt?: string | null
    lastRunAt?: string | null
    lastResult: PdfCleanupCounts
    lastStatus?: string | null
    lastStorageStatus?: string | null
    lastStorageAt?: string | null
  }
  eligible: PdfCleanupCounts
  storage?: { destination: string; folder?: string | null; choices: { value: string; label: string }[]; pendingDeletes: number } | null
}

export const generatedPdfCleanupApi = {
  get: () => api.get<PdfCleanupView>('/admin/storage-pdf-cleanup').then(response => response.data),
  update: (input: PdfCleanupInput) => api.put<PdfCleanupView>('/admin/storage-pdf-cleanup', input).then(response => response.data),
  clear: () => api.post<PdfCleanupCounts>('/admin/storage-pdf-cleanup/clear').then(response => response.data),
}
