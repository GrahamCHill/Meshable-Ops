import { useCallback, useEffect, useRef, useState } from 'react'
import { embedDashboard } from '@superset-ui/embedded-sdk'
import { supersetApi } from '../api/superset'
import { useTheme } from './useTheme'
import { applyEmbedTheme, stretchIframe, syncEmbedFrame } from '../lib/superset/embedFrame'

export interface DashboardEmbedOptions {
  dashboardId: string
  guestTokenResourceId?: string
  title?: string
  onLoad?: () => void
  onError?: (err: Error) => void
  onEscape?: () => void
}

export function useDashboardEmbed({ dashboardId, guestTokenResourceId, title = 'Embedded dashboard', onLoad, onError, onEscape }: DashboardEmbedOptions) {
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
  const fetchGuestToken = useCallback(async () => {
    const resourceIds = [
      guestTokenResourceId?.trim(),
      dashboardId.trim(),
    ].filter((value, index, values): value is string => !!value && values.indexOf(value) === index)

    let lastError: unknown
    for (const resourceId of resourceIds) {
      try {
        return await supersetApi.guestToken([resourceId], window.location.origin)
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
      stretchIframe(containerRef.current, modeRef.current)
      iframeObserverRef.current?.disconnect()
      try {
        const doc = frame?.contentDocument
        if (doc?.body) {
          observedDocument?.removeEventListener('keydown', handleKey)
          observedDocument = doc
          doc.addEventListener('keydown', handleKey)
          iframeObserverRef.current = new MutationObserver(() => { syncEmbedFrame(containerRef.current?.querySelector('iframe'), modeRef.current) })
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
      stretchIframe(containerRef.current, modeRef.current)
    })
    const resizeObserver = new ResizeObserver(() => stretchIframe(containerRef.current, modeRef.current))
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

    void mount()
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
  }, [dashboardId, fetchGuestToken, title, attempt])

  useEffect(() => {
    applyEmbedTheme(containerRef.current?.querySelector('iframe'), mode)
  }, [mode])
  return { containerRef, status, errorMsg, retry: () => setAttempt(value => value + 1) }
}
