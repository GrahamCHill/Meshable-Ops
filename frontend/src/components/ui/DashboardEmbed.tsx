import { useCallback, useEffect, useRef, useState } from 'react'
import { embedDashboard } from '@superset-ui/embedded-sdk'
import api from '../../api/client'
import { useTheme } from '../../hooks/useTheme'
import { applySupersetTheme } from '../../lib/supersetTheme'
import { Button } from './index'

interface DashboardEmbedProps {
  dashboardId: string
  guestTokenResourceId?: string
  title?: string
  height?: string | number
  onLoad?: () => void
  onError?: (err: Error) => void
  onEscape?: () => void
}

export function DashboardEmbed({
  dashboardId,
  guestTokenResourceId,
  title = 'Embedded dashboard',
  height = 600,
  onLoad,
  onError,
  onEscape,
}: DashboardEmbedProps) {
  const containerRef = useRef<HTMLDivElement>(null)
  const iframeObserverRef = useRef<MutationObserver | null>(null)
  const [status, setStatus] = useState<'loading' | 'ready' | 'error'>('loading')
  const [errorMsg, setErrorMsg] = useState('')
  const [attempt, setAttempt] = useState(0)
  const { mode } = useTheme()
  const modeRef = useRef(mode)
  modeRef.current = mode
  const callbacks = useRef({ onLoad, onError, onEscape })
  callbacks.current = { onLoad, onError, onEscape }

  const applyEmbedTheme = useCallback(() => {
    const iframe = containerRef.current?.querySelector('iframe')
    if (!iframe) return

    let doc: Document | null = null
    try {
      doc = iframe.contentDocument
    } catch {
      return
    }
    if (!doc) return

    applySupersetTheme(doc, modeRef.current)
  }, [])

  const hideEmbedActions = useCallback(() => {
    const iframe = containerRef.current?.querySelector('iframe')
    if (!iframe) return

    let doc: Document | null = null
    try {
      doc = iframe.contentDocument
    } catch {
      return
    }
    if (!doc) return

    const styleId = 'superset-embed-hide-actions'
    if (!doc.getElementById(styleId)) {
      const style = doc.createElement('style')
      style.id = styleId
      style.textContent = `
        [aria-label*="Share" i],
        [aria-label*="Save as" i],
        [aria-label*="Edit chart" i],
        [aria-label*="View query" i],
        [title*="Share" i],
        [title*="Save as" i],
        [title*="Edit chart" i],
        [title*="View query" i],
        [data-test*="share" i],
        [data-testid*="share" i],
        [data-test*="save-as" i],
        [data-testid*="save-as" i],
        [data-test*="edit-chart" i],
        [data-testid*="edit-chart" i],
        [data-test*="view-query" i],
        [data-testid*="view-query" i] {
          display: none !important;
        }
      `
      doc.head?.appendChild(style)
    }

    const alwaysBlockedLabels = new Set([
      'share',
      'share chart',
      'share dashboard',
      'save as',
      'edit chart',
      'view query',
    ])
    const actionNodes = doc.querySelectorAll<HTMLElement>('button, a, li, [role="button"], [role="menuitem"]')
    actionNodes.forEach((node) => {
      const text = (node.textContent ?? '').trim().toLowerCase()
      const aria = (node.getAttribute('aria-label') ?? '').trim().toLowerCase()
      const title = (node.getAttribute('title') ?? '').trim().toLowerCase()
      const testId = `${node.dataset.test ?? ''} ${node.dataset.testid ?? ''}`.toLowerCase()
      const isAlwaysBlocked =
        alwaysBlockedLabels.has(text) ||
        alwaysBlockedLabels.has(aria) ||
        alwaysBlockedLabels.has(title) ||
        testId.includes('share') ||
        testId.includes('save-as') ||
        testId.includes('edit-chart') ||
        testId.includes('view-query')
      if (isAlwaysBlocked) {
        node.style.display = 'none'
      }
    })
  }, [])

  const constrainIframeNavigation = useCallback(() => {
    const iframe = containerRef.current?.querySelector('iframe')
    if (!iframe) return

    let doc: Document | null = null
    let frameWindow: Window | null = null
    try {
      doc = iframe.contentDocument
      frameWindow = iframe.contentWindow
    } catch {
      return
    }
    if (!doc || !frameWindow) return

    const navHookKey = '__supersetNavigationHooked__'
    if ((frameWindow as Window & { [navHookKey]?: boolean })[navHookKey]) {
      doc.querySelectorAll<HTMLAnchorElement>('a[target]').forEach((anchor) => {
        anchor.target = '_self'
        anchor.rel = 'noopener noreferrer'
      })
      return
    }

    ;(frameWindow as Window & { [navHookKey]?: boolean })[navHookKey] = true

    try {
      frameWindow.open = ((url?: string | URL) => {
        if (typeof url === 'string' && url) {
          frameWindow!.location.assign(url)
        } else if (url instanceof URL) {
          frameWindow!.location.assign(url.toString())
        }
        return frameWindow
      }) as typeof window.open
    } catch {
      // Ignore if the browser prevents overriding open on the iframe window.
    }

    doc.querySelectorAll<HTMLAnchorElement>('a[target]').forEach((anchor) => {
      anchor.target = '_self'
      anchor.rel = 'noopener noreferrer'
    })

    doc.addEventListener('click', (event) => {
      const target = event.target
      if (!target || (target as Element).nodeType !== 1) return
      const anchor = (target as Element).closest('a[href]') as HTMLAnchorElement | null
      if (!anchor) return
      if (anchor.target && anchor.target !== '_self') {
        anchor.target = '_self'
      }
    }, true)
  }, [])

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

    applyEmbedTheme()
    hideEmbedActions()
    constrainIframeNavigation()
  }, [applyEmbedTheme, constrainIframeNavigation, hideEmbedActions])

  const fetchGuestToken = useCallback(async () => {
    const resourceIds = [
      guestTokenResourceId?.trim(),
      dashboardId.trim(),
    ].filter((value, index, values): value is string => !!value && values.indexOf(value) === index)

    let lastError: unknown
    for (const resourceId of resourceIds) {
      try {
        const res = await api.post<{ token: string }>('/superset/guest-token', {
          dashboardIds: [resourceId],
          appOrigin: window.location.origin,
        })
        return res.data.token
      } catch (error) {
        lastError = error
      }
    }

    throw lastError instanceof Error ? lastError : new Error('Could not obtain Superset guest token')
  }, [dashboardId, guestTokenResourceId])

  useEffect(() => {
    if (!containerRef.current) return
    // Each attempt owns its mount, so a late SDK response cannot clear a newer frame.
    const mountPoint = document.createElement('div')
    mountPoint.style.cssText = 'width:100%;height:100%'
    containerRef.current.replaceChildren(mountPoint)
    setStatus('loading')
    setErrorMsg('')
    let destroyed = false
    let embedded: Awaited<ReturnType<typeof embedDashboard>> | undefined
    let frame: HTMLIFrameElement | null = null
    let observedDocument: Document | null = null
    const handleKey = (event: KeyboardEvent) => { if (event.key === 'Escape') callbacks.current.onEscape?.() }
    const syncFrame = () => {
      if (destroyed) return
      stretchIframe()
      iframeObserverRef.current?.disconnect()
      try {
        const doc = frame?.contentDocument
        if (doc?.body) {
          observedDocument?.removeEventListener('keydown', handleKey)
          observedDocument = doc
          doc.addEventListener('keydown', handleKey)
          iframeObserverRef.current = new MutationObserver(() => { applyEmbedTheme(); hideEmbedActions(); constrainIframeNavigation() })
          iframeObserverRef.current.observe(doc.body, { childList: true, subtree: true })
        }
      } catch { /* The frame may be navigating. */ }
    }
    const observer = new MutationObserver(() => {
      const next = mountPoint.querySelector('iframe')
      if (next !== frame) {
        frame?.removeEventListener('load', syncFrame)
        frame = next
        frame?.addEventListener('load', syncFrame)
      }
      stretchIframe()
    })
    const resizeObserver = new ResizeObserver(() => stretchIframe())
    observer.observe(mountPoint, { childList: true, subtree: true })
    resizeObserver.observe(containerRef.current)
    const timeout = window.setTimeout(() => {
      if (!destroyed) { setStatus('error'); setErrorMsg('The dashboard is taking too long to respond. Try loading it again.') }
    }, 30_000)

    const mount = async () => {
      try {
        embedded = await embedDashboard({
          id: dashboardId,
          supersetDomain: window.location.origin,
          mountPoint,
          fetchGuestToken,
          iframeTitle: title,
          dashboardUiConfig: {
            hideTitle: true,
            hideChartControls: false,
            hideTab: false,
            filters: { expanded: false },
          },
        })
        if (destroyed) { embedded.unmount(); return }
        syncFrame()
        window.clearTimeout(timeout)
        if (!destroyed) {
          setStatus('ready')
          callbacks.current.onLoad?.()
        }
      } catch (err) {
        if (!destroyed) {
          const error = err instanceof Error ? err : new Error(String(err))
          setStatus('error')
          setErrorMsg(error.message)
          window.clearTimeout(timeout)
          callbacks.current.onError?.(error)
        }
      }
    }

    mount()
    return () => {
      destroyed = true
      window.clearTimeout(timeout)
      frame?.removeEventListener('load', syncFrame)
      observedDocument?.removeEventListener('keydown', handleKey)
      iframeObserverRef.current?.disconnect()
      observer.disconnect()
      resizeObserver.disconnect()
      embedded?.unmount()
      mountPoint.remove()
    }
  }, [applyEmbedTheme, constrainIframeNavigation, dashboardId, fetchGuestToken, hideEmbedActions, stretchIframe, title, attempt])

  useEffect(() => {
    applyEmbedTheme()
  }, [applyEmbedTheme, mode])

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

          gap: 12,
          color: 'var(--color-text-secondary)',
          fontSize: '0.875rem',
        }}>

          Loading dashboard...
        </div>
      )}
      {status === 'error' && (
        <div role="alert" style={{ position: 'absolute', inset: 0, padding: '2rem', textAlign: 'center', background: 'var(--color-surface)' }}>
          <div style={{ color: 'var(--color-danger)', fontWeight: 600, marginBottom: 8 }}>Dashboard failed to load</div>
          <div style={{ color: 'var(--color-text-secondary)', fontSize: '0.8rem', fontFamily: 'monospace' }}>{errorMsg}</div>
          <Button variant="secondary" style={{ marginTop: 16 }} onClick={() => setAttempt(value => value + 1)}>Try again</Button>
        </div>
      )}
      <div
        ref={containerRef}
        style={{
          width: '100%',
          height: '100%',

          overflow: 'hidden',

          visibility: status === 'error' ? 'hidden' : 'visible',
        }}
      />
    </div>
  )
}
