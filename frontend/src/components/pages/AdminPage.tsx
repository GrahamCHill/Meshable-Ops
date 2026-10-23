import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { adminStackApi, healthApi } from '../../api/system'
import { adminUsersApi } from '../../api/users'
import { Badge, Card, Spinner } from '../ui'
import { AdminPageHeader } from '../admin/AdminPageHeader'

import { adminGroups } from '../admin/adminNavigation'

export function AdminPage() {
  const usersQuery = useQuery({ queryKey: ['admin-users'], queryFn: adminUsersApi.list })
  const healthQuery = useQuery({ queryKey: ['admin-health'], queryFn: healthApi.check, refetchInterval: 30_000 })
  const stackQuery = useQuery({ queryKey: ['admin-stack'], queryFn: adminStackApi.get, refetchInterval: 30_000 })
  const servicesUp = stackQuery.data?.services.filter(service => service.status === 'UP').length ?? 0
  const serviceHealthLabel = healthQuery.data?.postgres === 'UP' ? 'Operational' : 'Needs attention'

  return (
    <div className="page-stack">
      <AdminPageHeader
        title="Administration"
        description="Choose a section to manage your workspace."
        actions={<Badge color="accent">Admin only</Badge>}
      />

      <nav className="page-stack" aria-label="Administration menu">
        {adminGroups.map(group => <section key={group.label}>
          <h2 className="card-title">{group.label}</h2>
          <div className="admin-cards">
            {group.items.map(({ to, label, description }) => (
              <Link className="admin-area-link" key={to} to={to}>
                <h3>{label}</h3>
                <p>{description}</p>
              </Link>
            ))}
          </div>
        </section>)}
      </nav>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(210px, 1fr))', gap: '0.75rem' }}>
        <Card style={{ padding: '1rem' }}>
          <div style={{ color: 'var(--color-text-secondary)', fontSize: '0.78rem', marginBottom: 6 }}>Accounts</div>
          <div style={{ fontSize: '1.3rem', fontWeight: 700 }}>{usersQuery.isLoading ? <Spinner /> : usersQuery.data?.length ?? 0}</div>
        </Card>
        <Card style={{ padding: '1rem' }}>
          <div style={{ color: 'var(--color-text-secondary)', fontSize: '0.78rem', marginBottom: 6 }}>Service health</div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, fontWeight: 700 }}>

            {healthQuery.isLoading ? 'Checking…' : serviceHealthLabel}
          </div>
        </Card>
        <Card style={{ padding: '1rem' }}>
          <div style={{ color: 'var(--color-text-secondary)', fontSize: '0.78rem', marginBottom: 6 }}>Services online</div>
          <div style={{ fontSize: '1.3rem', fontWeight: 700 }}>{stackQuery.isLoading ? <Spinner /> : `${servicesUp} / ${stackQuery.data?.services.length ?? 0}`}</div>
        </Card>
      </div>

    </div>
  )
}
