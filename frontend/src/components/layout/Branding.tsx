import { useEffect } from 'react'
import { useQuery } from '@tanstack/react-query'
import { brandImage, brandingApi, defaultBrandImage } from '../../api/branding'
import { sidebarForeground, validColour } from './brandingColours'

export function useBranding() {
  return useQuery({ queryKey: ['branding'], queryFn: brandingApi.current, staleTime: 60_000, refetchInterval: 60_000, retry: false })
}

export function Brand({ className = '' }: { className?: string }) {
  const { data } = useBranding()
  const name = data?.settings?.applicationName ?? 'WeaveOps'
  return <div className={`brand ${className}`} title={name}><img className="brand-logo" src={brandImage(data, 'LOGO')} alt="" onError={event => { if (!event.currentTarget.src.endsWith(defaultBrandImage)) event.currentTarget.src = defaultBrandImage }} /><span>{name}</span></div>
}

export function BrandingHead() {
  const { data } = useBranding()
  useEffect(() => {
    document.title = data?.settings?.applicationName ?? 'WeaveOps'
    const root = document.documentElement.style
    const settings = data?.settings
    const background = validColour(settings?.sidebarBackgroundColor) ? settings.sidebarBackgroundColor : undefined
    const foreground = validColour(settings?.sidebarTextColor) ? settings.sidebarTextColor : background ? sidebarForeground(background) : undefined
    const colours = { '--brand-sidebar-background': background, '--brand-sidebar-text': foreground, '--brand-link-colour': validColour(settings?.linkColor) ? settings.linkColor : undefined }
    for (const [key, value] of Object.entries(colours)) { if (value) root.setProperty(key, value); else root.removeProperty(key) }
    return () => { for (const key of Object.keys(colours)) root.removeProperty(key) }
  }, [data?.settings])
  useEffect(() => {
    const icon = document.querySelector<HTMLLinkElement>('#app-favicon')
    if (!icon) return
    const kind = data?.faviconVersion ? 'FAVICON' : 'LOGO'
    icon.href = brandImage(data, kind)
    icon.type = (kind === 'FAVICON' ? data?.faviconVersion : data?.logoVersion) ? 'image/png' : 'image/svg+xml'
  }, [data])
  return null
}
