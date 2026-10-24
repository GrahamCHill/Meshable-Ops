import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'

import { Button, Spinner } from '../ui'
import { AdminPageHeader } from '../admin/AdminPageHeader'
import { authApi } from '../../api/auth'
import { useTheme } from '../../hooks/useTheme'
import { applySupersetTheme } from '../../lib/supersetTheme'

const views = [
  { id: 'dashboards', label: 'Dashboards', path: '/dashboard/list/' },
  { id: 'charts', label: 'Charts', path: '/chart/list/' },
  { id: 'datasets', label: 'Datasets', path: '/tablemodelview/list/' },
]

export function SupersetAdminPage() {
  const navigate = useNavigate()
  const [params, setParams] = useSearchParams()
  const view = views.find(option => option.id === params.get('view')) ?? views[0]
  const [activeSection, setActiveSection] = useState(view.id)
  const [preparing, setPreparing] = useState(true)
  const [loading, setLoading] = useState(true)
  const [slow, setSlow] = useState(false)
  const [revision, setRevision] = useState(0)
  const [focused, setFocused] = useState(false)
  const iframeRef = useRef<HTMLIFrameElement | null>(null)
  const observerRef = useRef<MutationObserver | null>(null)
  const frameDocument = useRef<Document | null>(null)
  const handleFrameKey = useCallback((event: KeyboardEvent) => { if (event.key === 'Escape') setFocused(false) }, [])
  const { mode } = useTheme()
  const modeRef = useRef(mode)
  modeRef.current = mode
  const syncIframeTheme = useCallback(() => {
    try {
      const doc = iframeRef.current?.contentDocument
      if (doc) applySupersetTheme(doc, modeRef.current)
      const path = iframeRef.current?.contentWindow?.location.pathname ?? ''
      if (/^\/(?:superset\/)?dashboard(?:\/|$)/.test(path)) setActiveSection('dashboards')
      else if (/^\/(?:superset\/)?(?:chart|explore)(?:\/|$)/.test(path)) setActiveSection('charts')
      else if (/^\/(?:tablemodelview|datasetmodelview)(?:\/|$)/.test(path)) setActiveSection('datasets')
    } catch { /* The frame may be navigating. */ }
  }, [])

  useEffect(() => {
    let cancelled = false
    authApi.refreshSessionCookie().then(() => { if (!cancelled) setPreparing(false) }).catch(() => { if (!cancelled) navigate('/login', { replace: true }) })
    return () => { cancelled = true }
  }, [navigate])
  useEffect(() => { syncIframeTheme() }, [mode, syncIframeTheme])
  useEffect(() => () => {
    observerRef.current?.disconnect()
    frameDocument.current?.removeEventListener('keydown', handleFrameKey)
  }, [handleFrameKey])
  useEffect(() => {
    if (!loading || preparing) return
    const timeout = window.setTimeout(() => setSlow(true), 20_000)
    return () => window.clearTimeout(timeout)
  }, [loading, preparing, revision, view.id])
  useEffect(() => {
    if (!focused) return
    const close = (event: KeyboardEvent) => { if (event.key === 'Escape') setFocused(false) }
    window.addEventListener('keydown', close)
    return () => window.removeEventListener('keydown', close)
  }, [focused])
  const openList = (id: string) => {
    setActiveSection(id)
    setLoading(true); setSlow(false); setRevision(value => value + 1)
    setParams(previous => { const next = new URLSearchParams(previous); next.set('view', id); return next })
  }
  const reload = () => {
    setLoading(true); setSlow(false)
    try { iframeRef.current?.contentWindow?.location.reload() } catch { setRevision(value => value + 1) }
  }

  return (
    <div className="page-stack">
      <AdminPageHeader title="Superset workspace" description="Build your analytics here, then use Dashboard links to share them with your team."  actions={<Link className="button button-secondary button-sm" to="/admin/dashboard-links"> Dashboard links</Link>} />
      <section className={`analytics-workspace builder-workspace${focused ? ' is-focused' : ''}`} aria-label="Superset authoring workspace">
        <div className="workspace-toolbar">
          <nav className="workspace-tabs" aria-label="Superset sections">{views.map(({ id, label }) => <button key={id} className={activeSection === id ? 'is-active' : ''} aria-current={activeSection === id ? 'page' : undefined} onClick={() => openList(id)}>{label}</button>)}</nav>
          <div className="inline-actions"><Button variant="ghost" size="sm" disabled={preparing} onClick={() => openList(activeSection)}> Return to list</Button><button className="utility-button" aria-label="Reload Superset workspace" title="Reload current page" disabled={preparing} onClick={reload}>Reload</button><button className="utility-button" aria-label={focused ? 'Exit expanded view' : 'Expand Superset workspace'} title={focused ? 'Exit expanded view (Esc)' : 'Expand workspace'} onClick={() => setFocused(!focused)}>{focused ? 'Exit expanded view' : 'Expand'}</button></div>
        </div>
        <p className="workspace-description">Sign in with your separate Superset analytics account to create and edit reports. Use the tabs above to switch workspaces.</p>
        {slow && <div role="status" className="feedback warning workspace-description">Superset is taking longer than expected. <button className="text-button" onClick={reload}>Reload workspace</button></div>}
        <div className="workspace-content builder-frame">
          {(preparing || loading) && <div className="frame-loading" role="status"><Spinner />{preparing ? 'Preparing your session…' : `Opening ${view.label.toLowerCase()}…`}</div>}
          {!preparing && <iframe key={`${view.id}-${revision}`} ref={iframeRef} src={view.path} title={`Superset ${view.label}`} onError={() => { setLoading(false); setSlow(true) }} onLoad={() => {
            setLoading(false); setSlow(false); syncIframeTheme()
            observerRef.current?.disconnect()
            try {
              const doc = iframeRef.current?.contentDocument
              if (doc?.body) {
                frameDocument.current?.removeEventListener('keydown', handleFrameKey)
                frameDocument.current = doc
                doc.addEventListener('keydown', handleFrameKey)
                observerRef.current = new MutationObserver(syncIframeTheme)
                observerRef.current.observe(doc.body, { childList: true, subtree: true })
              }
            } catch { /* A subsequent frame load will retry. */ }
          }} />}
        </div>
      </section>
    </div>
  )
}
