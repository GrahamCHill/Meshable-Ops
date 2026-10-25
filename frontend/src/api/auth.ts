import api from './http'
import type { MfaChallenge } from './mfa'

export interface AuthResponse {
  token: string
  id: string
  email: string
  username: string
  name?: string
  contactNumber?: string
  role: string
  canUploadDocuments: boolean
  suggestMfaSetup?: boolean
}

export interface ExternalProvider { id: string; name: string; authorizationUrl: string }

export const authApi = {
  providers: () => api.get<ExternalProvider[]>('/auth/providers').then(r => r.data),
  completeExternal: () => api.post<{auth?: AuthResponse | MfaChallenge; linked: boolean}>('/auth/oidc/complete').then(r => r.data),
  identities: () => api.get<string[]>('/account/identities').then(r => r.data),
  connectIdentity: (provider: string) => api.post<{authorizationUrl: string}>(`/account/identities/${encodeURIComponent(provider)}/connect`).then(r => r.data),
  disconnectIdentity: (provider: string) => api.delete(`/account/identities/${encodeURIComponent(provider)}`),
  register: (data: { email: string; username: string; password: string }) =>
    api.post<AuthResponse>('/auth/register', data).then(r => r.data),
  login: (data: { email: string; password: string }) =>
    api.post<AuthResponse | MfaChallenge>('/auth/login', data).then(r => r.data),
  refreshSessionCookie: (signal?: AbortSignal) =>
    api.post<AuthResponse>('/auth/session-cookie', undefined, {signal}).then(r => r.data),
  logout: () =>
    api.post('/auth/logout').then(r => r.data),
}
