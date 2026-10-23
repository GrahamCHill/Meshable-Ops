import api from './http'

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
