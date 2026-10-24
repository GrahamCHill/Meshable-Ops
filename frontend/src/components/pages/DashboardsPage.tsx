import { useAccess } from '../../api/access'
import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { dashboardEmbedsApi } from '../../api/dashboards'
import { Button, Card, Spinner } from '../ui'
import { DashboardEmbed } from '../ui/DashboardEmbed'

export function DashboardsPage() {
  const [params, setParams] = useSearchParams()
  const [revision, setRevision] = useState(0)
  const [focused, setFocused] = useState(false)
  const admin = useAccess().can('admin.dashboards')
  const embedsQuery = useQuery({ queryKey: ['dashboard-embeds'], queryFn: dashboardEmbedsApi.list })
  const dashboards = [...(embedsQuery.data ?? [])].sort((a, b) => a.sortOrder - b.sortOrder || a.label.localeCompare(b.label))
  const selectedId = params.get('dashboard')
  const current = dashboards.find(dashboard => dashboard.id === selectedId) ?? dashboards[0]

  useEffect(() => {
    if (current && selectedId !== current.id) setParams(previous => { const next = new URLSearchParams(previous); next.set('dashboard', current.id); return next }, { replace: true })
  }, [current?.id, selectedId, setParams])
  useEffect(() => {
    if (!focused) return
    const onKey = (event: KeyboardEvent) => { if (event.key === 'Escape') setFocused(false) }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [focused])
  const dashboardLink = (id: string) => {
    const next = new URLSearchParams(params)
    next.set('dashboard', id)
    return { search: `?${next.toString()}` }
  }

  return (
    <div className="page-stack">
      <div className="page-heading"><div><h1>Dashboards</h1><p>Explore the reports available to your team.</p></div>{admin && <Link className="button button-secondary button-sm" to="/admin/dashboard-links"> Manage dashboard links</Link>}</div>
      {embedsQuery.isLoading && <Card><div className="empty-state"><Spinner /> Loading dashboards…</div></Card>}
      {embedsQuery.isError && <Card><div className="empty-state"><h2>Unable to load dashboards</h2><p>Check your connection and try again.</p><Button variant="secondary" onClick={() => embedsQuery.refetch()}> Try again</Button></div></Card>}
      {!embedsQuery.isLoading && !embedsQuery.isError && !current && <Card><div className="empty-state"><h2>No dashboards available</h2><p>{admin ? 'Link a published Superset dashboard to make it available here.' : 'Your administrator can share dashboards with your role.'}</p>{admin && <Link className="button button-primary button-md" to="/admin/dashboard-links">Link a dashboard</Link>}</div></Card>}
      {current && !embedsQuery.isError && <section className={`analytics-workspace${focused ? ' is-focused' : ''}`} aria-label="Dashboard viewer">
        <nav className="dashboard-navigation" aria-labelledby="dashboard-navigation-heading">
          <div className="dashboard-navigation-heading">
            <h2 id="dashboard-navigation-heading">Choose a dashboard</h2>
            <span>{dashboards.length} available</span>
          </div>
          <ul>
            {dashboards.map(dashboard => <li key={dashboard.id}>
              <Link to={dashboardLink(dashboard.id)} aria-current={dashboard.id === current.id ? 'page' : undefined}>
                {dashboard.label}
              </Link>
            </li>)}
          </ul>
        </nav>
        <div className="workspace-toolbar">
          <div className="dashboard-summary" aria-live="polite" aria-atomic="true">
            <h2>{current.label}</h2>
            {current.description && <p className="workspace-description">{current.description}</p>}
          </div>
          <div className="inline-actions">
            <button className="utility-button" aria-label="Reload dashboard" title="Reload dashboard" onClick={() => setRevision(value => value + 1)}>Reload</button>
            <button className="utility-button" aria-label={focused ? 'Exit expanded view' : 'Expand dashboard'} title={focused ? 'Exit expanded view (Esc)' : 'Expand dashboard'} onClick={() => setFocused(!focused)}>{focused ? 'Exit expanded view' : 'Expand'}</button>
          </div>
        </div>
        <div className="workspace-content"><DashboardEmbed key={`${current.id}-${revision}`} dashboardId={current.dashboardId} guestTokenResourceId={current.guestTokenResourceId} title={current.label} height="100%" onEscape={() => setFocused(false)} /></div>
      </section>}
    </div>
  )
}
