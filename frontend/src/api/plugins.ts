import { useQuery } from '@tanstack/react-query'
import api from './http'
import { useAuthStore } from '../hooks/useAuthStore'

export interface PluginView {
  manifest: { id: string; name: string; navigationLabel: string; version: string; apiVersion: number; description: string; extensions: ('PAGE' | 'API' | 'DATABASE' | 'SEARCH' | 'SETTINGS' | 'NOTIFICATIONS')[]; menuItems: {path: string; label: string; roles: string[]}[]; tables: {name: string; label: string}[]; settingsFields: PluginSettingField[]; supportedRoles: string[]; capabilities: Record<string,string>; requiresCapabilities: string[] }
  enabled: boolean
  active: boolean
  missingDependencies: string[]
  features: {id: string; label: string; available: boolean; providers: string[]; missing: string[]}[]
  allowedRoles: string[]
  searchEnabled: boolean
  tableAccess: Record<string, {readRoles: string[]; writeRoles: string[]}>
  source: 'BUNDLED' | 'ZIP'
  blockedBySecurity: boolean
  author?: { name: string; organization: string; email: string; website: string }
}
export interface PluginSettingField {key: string; label: string; type: 'TEXT' | 'SECRET'; required: boolean; help: string; maxLength: number}
export interface PluginConfiguration {values: Record<string, string>; configuredSecrets: string[]; missingRequired: string[]; revision: number; encryptionConfigured: boolean}
export type PluginSettings = Pick<PluginView, 'enabled' | 'allowedRoles' | 'searchEnabled' | 'tableAccess'>
export const pluginsApi = {
  install: (file: File) => { const body = new FormData(); body.append('file', file); return api.post<PluginView>('/admin/plugins/install', body, {headers: {'Content-Type': undefined}}).then(response => response.data) },
  runtime: (id: string, section: string) => api.get<{html: string; configuration: Record<string, string>}>(`/plugins/${encodeURIComponent(id)}/runtime`, {params: {section}}).then(response => response.data),
  available: () => api.get<PluginView[]>('/plugins').then(response => response.data),
  installed: () => api.get<PluginView[]>('/admin/plugins').then(response => response.data),
  configure: (id: string, settings: PluginSettings) => api.put<PluginView>(`/admin/plugins/${encodeURIComponent(id)}`, settings).then(response => response.data),
  clear: (id: string, confirmation: string) => api.post<{deletedRows: Record<string, number>}>(`/admin/plugins/${encodeURIComponent(id)}/clear-data`, {confirmation}).then(response => response.data),
  configuration: (id: string) => api.get<PluginConfiguration>(`/admin/plugins/${encodeURIComponent(id)}/configuration`).then(response => response.data),
  saveConfiguration: (id: string, values: Record<string, string>, clearSecrets: string[], revision: number) => api.put<PluginConfiguration>(`/admin/plugins/${encodeURIComponent(id)}/configuration`, {values, clearSecrets, revision}).then(response => response.data),
}
export interface ExtensionSecurity { allowThirdPartyExtensions: boolean; revision: number }
export const extensionSecurityApi = {
  get: () => api.get<ExtensionSecurity>('/admin/security/extensions').then(response => response.data),
  save: (settings: ExtensionSecurity) => api.put<ExtensionSecurity>('/admin/security/extensions', settings).then(response => response.data),
}
export function useAvailablePlugins() {
  const { token, user } = useAuthStore()
  return useQuery({ queryKey: ['plugins', user?.id, user?.role], queryFn: pluginsApi.available, enabled: !!token && !!user, refetchInterval: 30_000 })
}
