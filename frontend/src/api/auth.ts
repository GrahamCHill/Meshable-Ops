import api from './http'

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
