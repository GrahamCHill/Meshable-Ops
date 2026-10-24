import { useEffect } from 'react'
import { useQuery } from '@tanstack/react-query'
import { brandImage, brandingApi, defaultBrandImage } from '../../api/branding'

export function useBranding() {
  return useQuery({ queryKey: ['branding'], queryFn: brandingApi.current, staleTime: 60_000, refetchInterval: 60_000, retry: false })
}

export function Brand({ className = '' }: { className?: string }) {
  const { data } = useBranding()
  const name = data?.settings?.applicationName ?? 'Meshable Ops'
  return <div className={`brand ${className}`} title={name}><img className="brand-logo" src={brandImage(data, 'LOGO')} alt="" onError={event => { if (!event.currentTarget.src.endsWith(defaultBrandImage)) event.currentTarget.src = defaultBrandImage }} /><span>{name}</span></div>
}

export function BrandingHead() {
  const { data } = useBranding()
  useEffect(() => {
    document.title = data?.settings?.applicationName ?? 'Meshable Ops'
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
