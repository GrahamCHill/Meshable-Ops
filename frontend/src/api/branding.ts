import api from './http'

export type BrandingKind = 'LOGO' | 'FAVICON'
export interface BrandingSettings {
  applicationName: string
  sidebarBackgroundColor: string | null
  sidebarTextColor: string | null
  linkColor: string | null
  loginBackgroundMode: 'NONE' | 'SINGLE' | 'SLIDESHOW'
  loginBackgroundSlot: number | null
  slideshowSeconds: number
  revision: number
}
export interface LoginImage { slot: number; version: string }
export interface Branding { logoVersion: string | null; faviconVersion: string | null; settings: BrandingSettings; loginImages: LoginImage[] }
export const defaultBrandingSettings: BrandingSettings = { applicationName: 'WeaveOps', sidebarBackgroundColor: null, sidebarTextColor: null, linkColor: null, loginBackgroundMode: 'NONE', loginBackgroundSlot: null, slideshowSeconds: 8, revision: 0 }
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
