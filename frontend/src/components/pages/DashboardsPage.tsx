import { useEffect, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { dashboardEmbedsApi } from '../../api/client'
import { Card, Spinner } from '../ui'
import { DashboardEmbed } from '../ui/DashboardEmbed'
import { LayoutDashboard } from 'lucide-react'

export function DashboardsPage() {
  const [active, setActive] = useState(0)
  const embedsQuery = useQuery({ queryKey: ['dashboard-embeds'], queryFn: dashboardEmbedsApi.list })
  const dashboards = embedsQuery.data ?? []
  const current = dashboards[active]

  useEffect(() => {
    if (active > Math.max(dashboards.length - 1, 0)) setActive(0)
  }, [active, dashboards.length])

  return (
    <div style={{ width: '100%', maxWidth: 'none' }}>
      <div style={{ marginBottom: '1.25rem' }}>
        <h1 style={{ fontSize: '1.4rem', fontWeight: 700, marginBottom: 4 }}>Dashboards</h1>
        <p style={{ color: 'var(--color-text-secondary)', fontSize: '0.875rem' }}>Embedded Apache Superset dashboards available to your role</p>
      </div>
      {embedsQuery.isLoading && <div style={{ display: 'flex', justifyContent: 'center', padding: '3rem' }}><Spinner /></div>}
      {!embedsQuery.isLoading && dashboards.length > 1 && (
        <div style={{ display: 'flex', gap: 4, marginBottom: '1rem', borderBottom: '1px solid var(--color-border)', flexWrap: 'wrap' }}>
          {dashboards.map((dashboard, index) => <button key={dashboard.id} onClick={() => setActive(index)} style={{ padding: '8px 16px', fontSize: '0.875rem', fontWeight: active === index ? 600 : 400, color: active === index ? 'var(--color-text)' : 'var(--color-text-secondary)', background: 'none', border: 'none', borderBottom: active === index ? '2px solid var(--color-accent)' : '2px solid transparent', cursor: 'pointer', marginBottom: -1 }}>{dashboard.label}</button>)}
        </div>
      )}
      {!embedsQuery.isLoading && !current && (
        <Card style={{ padding: '3rem 2rem', textAlign: 'center' }}>
          <LayoutDashboard size={28} color="var(--color-accent)" style={{ marginBottom: 12 }} />
          <h2 style={{ fontSize: '1.1rem', fontWeight: 600, marginBottom: 8 }}>No dashboards available</h2>
          <p style={{ color: 'var(--color-text-secondary)', fontSize: '0.875rem' }}>An administrator can link published Superset dashboards from Admin → Dashboard Links.</p>
        </Card>
      )}
      {current && <><>{current.description && <p style={{ color: 'var(--color-text-secondary)', fontSize: '0.875rem', marginBottom: '0.75rem' }}>{current.description}</p>}</><DashboardEmbed key={current.id} dashboardId={current.dashboardId} guestTokenResourceId={current.guestTokenResourceId} height="calc(100vh - 12rem)" /></>}
    </div>
  )
}
