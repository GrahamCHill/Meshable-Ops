import api from './http'

export type BrandingKind = 'LOGO' | 'FAVICON'
export interface WorkspacePalette {
  background?: string | null; surface?: string | null; raised?: string | null; text?: string | null; textSecondary?: string | null
  link?: string | null; sidebarBackground?: string | null; sidebarText?: string | null
  tableHeaderBackground?: string | null; tableHeaderText?: string | null; headerBackground?: string | null; headerText?: string | null
}
export interface WorkspaceThemes { switchingEnabled: boolean; fixedMode: 'LIGHT' | 'DARK'; light: WorkspacePalette; dark: WorkspacePalette; custom: WorkspacePalette }
export const defaultWorkspaceThemes: WorkspaceThemes = { switchingEnabled: true, fixedMode: 'LIGHT', light: {}, dark: {}, custom: {} }
export interface BrandingSettings {
  applicationName: string
  companyName?: string
  sidebarBackgroundColor: string | null
  sidebarTextColor: string | null
  linkColor: string | null
  loginBackgroundMode: 'NONE' | 'SINGLE' | 'SLIDESHOW'
  loginBackgroundSlot: number | null
  slideshowSeconds: number
  revision: number
  themes: WorkspaceThemes
}
export interface LoginImage { slot: number; version: string }
export interface Branding { logoVersion: string | null; faviconVersion: string | null; settings: BrandingSettings; loginImages: LoginImage[] }
export const defaultBrandingSettings: BrandingSettings = { applicationName: 'Meshable Ops', companyName: '', sidebarBackgroundColor: null, sidebarTextColor: null, linkColor: null, loginBackgroundMode: 'NONE', loginBackgroundSlot: null, slideshowSeconds: 8, revision: 0, themes: defaultWorkspaceThemes }
export const defaultBrandImage = '/brand-mark.svg'
export const brandingApi = {
  current: () => api.get<Branding>('/branding').then(response => response.data),
  upload: (kind: BrandingKind, file: File) => {
    const data = new FormData(); data.append('file', file)
    return api.post<Branding>(`/admin/branding/${kind}`, data, { headers: { 'Content-Type': 'multipart/form-data' } }).then(response => response.data)
  },
  reset: (kind: BrandingKind) => api.delete<Branding>(`/admin/branding/${kind}`).then(response => response.data),
  save: (settings: BrandingSettings) => api.put<Branding>('/admin/branding', settings).then(response => response.data),
  uploadBackground: (slot: number, file: File) => {
    const data = new FormData(); data.append('file', file)
    return api.post<Branding>(`/admin/branding/login-images/${slot}`, data, { headers: { 'Content-Type': 'multipart/form-data' } }).then(response => response.data)
  },
  removeBackground: (slot: number) => api.delete<Branding>(`/admin/branding/login-images/${slot}`).then(response => response.data),
}
export function loginImageUrl(image: LoginImage) {
  return `${(api.defaults.baseURL ?? '/api').replace(/\/$/, '')}/branding/login-images/${image.slot}/${image.version}`
}
export function brandImage(branding: Branding | undefined, kind: BrandingKind) {
  const version = kind === 'LOGO' ? branding?.logoVersion : branding?.faviconVersion
  if (!version) return defaultBrandImage
  return `${(api.defaults.baseURL ?? '/api').replace(/\/$/, '')}/branding/assets/${kind}/${version}`
}
