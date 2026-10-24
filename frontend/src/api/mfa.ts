import api from './http'
import type { AuthResponse } from './auth'
export type MfaMethod = 'TOTP' | 'EMAIL' | 'RECOVERY'
export interface MfaChallenge { mfaRequired: true; challengeToken: string; methods: MfaMethod[]; expiresAt: string }
export interface MfaStatus { totpEnabled: boolean; emailEnabled: boolean; emailAvailable: boolean; encryptionReady: boolean; recoveryCodesRemaining: number; setupRecommended: boolean }
export interface MfaProof { password: string; method?: MfaMethod; code?: string; challengeToken?: string; target?: 'ALL' | 'TOTP' | 'EMAIL' }
export interface MfaEnrollment { challengeToken: string; expiresAt: string; secret?: string; otpauthUri?: string }
export interface MfaComplete { auth: AuthResponse; recoveryCodes: string[] }
export const mfaApi = {
  verify: (challengeToken: string, method: MfaMethod, code: string) => api.post<AuthResponse>('/auth/mfa/verify', { challengeToken, method, code }).then(r => r.data),
  sendLoginEmail: (challengeToken: string) => api.post<{ message: string }>('/auth/mfa/email', { challengeToken }).then(r => r.data),
  status: () => api.get<MfaStatus>('/account/mfa').then(r => r.data),
  dismiss: () => api.post('/account/mfa/dismiss'),
  setup: (kind: 'totp' | 'email' | 'manage-email', proof: MfaProof) => api.post<MfaEnrollment>(`/account/mfa/setup/${kind}`, proof).then(r => r.data),
  confirm: (challengeToken: string, method: MfaMethod, code: string) => api.post<MfaComplete>('/account/mfa/confirm', { challengeToken, method, code }).then(r => r.data),
  disable: (proof: MfaProof) => api.post<MfaComplete>('/account/mfa/disable', proof).then(r => r.data),
  recovery: (proof: MfaProof) => api.post<MfaComplete>('/account/mfa/recovery-codes', proof).then(r => r.data),
}
