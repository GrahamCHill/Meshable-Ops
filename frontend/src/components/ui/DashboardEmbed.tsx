import { useCallback, useEffect, useRef, useState } from 'react'
import { embedDashboard } from '@superset-ui/embedded-sdk'
import api from '../../api/client'

interface DashboardEmbedProps {
  dashboardId: string
  guestTokenResourceId?: string
  height?: string | number
  onLoad?: () => void
  onError?: (err: Error) => void
}

export function DashboardEmbed({
  dashboardId,
  guestTokenResourceId,
  height = 600,
  onLoad,
  onError,
}: DashboardEmbedProps) {
  const containerRef = useRef<HTMLDivElement>(null)
  const [status, setStatus] = useState<'loading' | 'ready' | 'error'>('loading')
  const [errorMsg, setErrorMsg] = useState('')
  const resourceId = guestTokenResourceId ?? dashboardId

  const stretchIframe = useCallback(() => {
    const mount = containerRef.current
    if (!mount) return

    const iframe = mount.querySelector('iframe')
    if (!iframe) return

    mount.style.width = '100%'
    mount.style.height = '100%'
    mount.style.minHeight = '100%'
    mount.style.display = 'block'

    iframe.style.width = '100%'
    iframe.style.height = '100%'
    iframe.style.minHeight = '100%'
    iframe.style.display = 'block'
    iframe.style.border = '0'
    iframe.setAttribute('width', '100%')
    iframe.setAttribute('height', '100%')

    let node = iframe.parentElement
    while (node && node !== mount) {
      node.style.width = '100%'
      node.style.height = '100%'
      node.style.minHeight = '100%'
      if (!node.style.display || node.style.display === 'inline') {
        node.style.display = 'block'
      }
      node = node.parentElement
    }
  }, [])

  const fetchGuestToken = useCallback(async () => {
    const res = await api.post<{ token: string }>('/superset/guest-token', {
      dashboardIds: [resourceId],
    })
    return res.data.token
  }, [resourceId])

  useEffect(() => {
    if (!containerRef.current) return

    let destroyed = false
    const observer = new MutationObserver(() => stretchIframe())
    const resizeObserver = new ResizeObserver(() => stretchIframe())
    observer.observe(containerRef.current, { childList: true, subtree: true })
    resizeObserver.observe(containerRef.current)

    const mount = async () => {
      try {
        await embedDashboard({
          id: dashboardId,
          supersetDomain: window.location.origin,
          mountPoint: containerRef.current!,
          fetchGuestToken,
          dashboardUiConfig: {
            hideTitle: false,
            hideChartControls: true,
            hideTab: true,
            filters: { expanded: true },
          },
        })
        stretchIframe()
        window.setTimeout(stretchIframe, 50)
        window.setTimeout(stretchIframe, 250)
        window.setTimeout(stretchIframe, 1000)
        if (!destroyed) {
          setStatus('ready')
          onLoad?.()
        }
      } catch (err) {
        if (!destroyed) {
          const error = err instanceof Error ? err : new Error(String(err))
          setStatus('error')
          setErrorMsg(error.message)
          onError?.(error)
        }
      }
    }

    mount()
    return () => {
      destroyed = true
      observer.disconnect()
      resizeObserver.disconnect()
    }
  }, [dashboardId, fetchGuestToken, onLoad, onError, stretchIframe])

  return (
    <div style={{ position: 'relative', width: '100%', height: typeof height === 'number' ? `${height}px` : height, minHeight: 0 }}>
      {status === 'loading' && (
        <div style={{
          position: 'absolute',
          inset: 0,
          zIndex: 1,
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          background: 'var(--color-surface)',
          borderRadius: 'var(--radius-md)',
          gap: 12,
          color: 'var(--color-text-secondary)',
          fontSize: '0.875rem',
        }}>
          <span style={{ width: 28, height: 28, border: '2px solid var(--color-border)', borderTopColor: 'var(--color-accent)', borderRadius: '50%', animation: 'spin 0.7s linear infinite', display: 'inline-block' }} />
          Loading dashboard...
        </div>
      )}
      {status === 'error' && (
        <div style={{ padding: '2rem', textAlign: 'center', background: 'var(--color-surface)', borderRadius: 'var(--radius-md)', border: '1px solid rgba(239,68,68,0.2)' }}>
          <div style={{ color: 'var(--color-danger)', fontWeight: 600, marginBottom: 8 }}>Dashboard failed to load</div>
          <div style={{ color: 'var(--color-text-secondary)', fontSize: '0.8rem', fontFamily: 'monospace' }}>{errorMsg}</div>
          <div style={{ color: 'var(--color-text-tertiary)', fontSize: '0.8rem', marginTop: 12 }}>
            Make sure the dashboard UUID is correct and embedding is enabled in Superset.
          </div>
        </div>
      )}
      <div
        ref={containerRef}
        style={{
          width: '100%',
          height: '100%',
          borderRadius: 'var(--radius-md)',
          overflow: 'hidden',
          border: '1px solid var(--color-border)',
          visibility: status === 'error' ? 'hidden' : 'visible',
        }}
      />
    </div>
  )
}
