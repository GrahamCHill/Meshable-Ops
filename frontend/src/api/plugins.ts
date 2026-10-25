import { useQuery } from '@tanstack/react-query'
import api from './http'
import { useAuthStore } from '../hooks/useAuthStore'

export interface PluginView {
  manifest: { id: string; name: string; navigationLabel: string; version: string; apiVersion: number; description: string; extensions: ('PAGE' | 'API' | 'DATABASE' | 'SEARCH' | 'SETTINGS' | 'NOTIFICATIONS')[]; menuItems: {path: string; label: string; roles: string[]}[]; tables: {name: string; label: string}[]; settingsFields: PluginSettingField[]; supportedRoles: string[]; capabilities: Record<string,string>; requiresCapabilities: string[]; contextTargets: ('PROJECT' | 'CLIENT')[];
    showPageBanner: boolean; categories: string[]; icon?: string; banner?: string; requires: string[]; extends: string[]; incompatibleWith: Record<string,string>; hasAdminSettings: boolean; adminMenu?: PluginAdminMenu; providesSms: boolean; placements: {slot: string; label: string; section: string; mode: 'LINK'|'PANEL'}[]; compatibleVersions: string[]; runtimes: ('REACT'|'KOTLIN'|'PYTHON')[]; settingsSections?: PluginSettingsSection[]; sharedSettings?: PluginSharedSettingsReference[]; sharedSettingsSchemas?: PluginSharedSettingsSchema[] }
  enabled: boolean
  active: boolean
  missingDependencies: string[]
  features: {id: string; label: string; available: boolean; providers: string[]; missing: string[]}[]
  allowedRoles: string[]
  searchEnabled: boolean
  tableAccess: Record<string, {readRoles: string[]; writeRoles: string[]}>
  source: 'BUNDLED' | 'ZIP'
  blockedBySecurity: boolean
  compatibilityIssues: string[]
  applicationVersion: string
  versionCompatibility: 'COMPATIBLE' | 'UNKNOWN' | 'INCOMPATIBLE'
  compatibilityWarnings: string[]
  removed: boolean
  settingsRevision: number
  author?: { name: string; organization: string; email: string; website: string }
}
export interface PluginSettingField {key: string; label: string; type: 'TEXT' | 'SECRET' | 'BOOLEAN'; required: boolean; help: string; maxLength: number; defaultValue?: string}
export interface PluginAdminMenu {category:string;categoryLabel:string;label:string;icon?:string}
export interface PluginSettingsSection {id:string;label:string;category:string;categoryLabel:string;description:string;fields:string[]}
export interface PluginSharedSettingsReference {id:string;schemaId:string;label:string;fields:string[];sectionId:string}
export interface PluginSharedSettingsSchema {id:string;label:string;description:string;fields:PluginSettingField[];adminMenu:PluginAdminMenu;identityFields:string[];httpOriginField?:string}
export interface PluginConfiguration {values: Record<string, string>; configuredSecrets: string[]; missingRequired: string[]; revision: number; encryptionConfigured: boolean}
export type PluginSettings = Pick<PluginView, 'enabled' | 'allowedRoles' | 'searchEnabled' | 'tableAccess'>
export const pluginsApi = {
  activation: (changes:{id:string;enabled:boolean;expectedRevision:number}[])=>api.put<PluginView[]>('/admin/plugins/activation',{changes}).then(response=>response.data),
  code: (id:string)=>api.get<{backend?:{modules:Record<string,{runtime:'PYTHON'|'KOTLIN';entrypoint:string}>;routes:{path:string;module:string;methods:string[];roles:string[];permissions:string[]}[];allowedOrigins:string[]};runnerConfigured:boolean;sha256:string}>(`/admin/plugins/${encodeURIComponent(id)}/code`).then(response=>response.data),
  remove: (id: string, confirmation: string) => api.delete(`/admin/plugins/${encodeURIComponent(id)}`, {data: {confirmation}}).then(() => undefined),
  restore: (id: string) => api.post(`/admin/plugins/${encodeURIComponent(id)}/restore`).then(() => undefined),
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
