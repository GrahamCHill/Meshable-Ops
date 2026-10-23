import api from './http'

export type EmailProvider = 'SMTP' | 'RESEND' | 'SENDGRID' | 'MAILGUN'
export interface EmailConfigurationView {
  enabled: boolean; provider: EmailProvider; fromAddress: string; fromName: string; replyTo: string
  smtpHost: string; smtpPort: number; smtpSecurity: 'STARTTLS' | 'TLS' | 'NONE'; smtpAuth: 'PASSWORD' | 'OAUTH2' | 'NONE'
  smtpUsernameConfigured: boolean; smtpPasswordConfigured: boolean; apiBaseUrl: string; apiKeyConfigured: boolean; mailgunDomain: string
}
export interface EmailSettings { source: 'ENVIRONMENT' | 'ADMIN'; encryptionReady: boolean; admin: EmailConfigurationView; environment: EmailConfigurationView }
export type EmailInput = Omit<EmailConfigurationView, 'smtpUsernameConfigured' | 'smtpPasswordConfigured' | 'apiKeyConfigured'> & {
  source: EmailSettings['source']; smtpUsername: string; smtpPassword: string; clearSmtpCredentials: boolean; apiKey: string; clearApiKey: boolean
}
export interface EmailMessage { recipient: string; subject: string; text: string }
export interface EmailResult { status: 'ACCEPTED'; message: string }
export const emailApi = {
  settings: () => api.get<EmailSettings>('/admin/email').then(r => r.data),
  save: (input: EmailInput | { source: 'ENVIRONMENT' }) => api.put<EmailSettings>('/admin/email', input).then(r => r.data),
  test: (recipient: string) => api.post<EmailResult>('/admin/email/test', { recipient }, { timeout: 90000 }).then(r => r.data),
  send: (message: EmailMessage) => api.post<EmailResult>('/admin/email/send', message, { timeout: 90000 }).then(r => r.data),
}
export function emailInput(view: EmailConfigurationView, source: EmailSettings['source']): EmailInput {
  const { smtpUsernameConfigured: _username, smtpPasswordConfigured: _password, apiKeyConfigured: _key, ...values } = view
  return { ...values, source, smtpUsername: '', smtpPassword: '', clearSmtpCredentials: false, apiKey: '', clearApiKey: false }
}
export const emailApiUrls: Record<EmailProvider, string> = { SMTP: '', RESEND: 'https://api.resend.com', SENDGRID: 'https://api.sendgrid.com', MAILGUN: 'https://api.mailgun.net/v3' }
