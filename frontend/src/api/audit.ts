import api from './http'
import type { PageResponse } from './types'

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
