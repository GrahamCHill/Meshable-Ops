import { useAccess } from '../../api/access'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { adminStackApi, healthApi } from '../../api/system'
import { adminUsersApi } from '../../api/users'
import { Badge, Card, Spinner } from '../ui'
import { AdminPageHeader } from '../admin/AdminPageHeader'

import { adminGroups } from '../admin/adminNavigation'

export function AdminPage() {
  const access = useAccess()
  const usersQuery = useQuery({ queryKey: ['admin-users'], queryFn: adminUsersApi.list, enabled: access.can('admin.users') })
  const healthQuery = useQuery({ queryKey: ['admin-health'], queryFn: healthApi.check, enabled: access.can('admin.system'), refetchInterval: 30_000 })
  const stackQuery = useQuery({ queryKey: ['admin-stack'], queryFn: adminStackApi.get, enabled: access.can('admin.system'), refetchInterval: 30_000 })
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
        <div className="admin-cards">{adminGroups.filter(group => group.items.some(item => access.canPath(item.to))).map(group =>
          <Link className="admin-area-link" key={group.id} to={`/admin/sections/${group.id}`}><h3>{group.label}</h3><p>{group.description}</p></Link>
        )}</div>
      </nav>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(210px, 1fr))', gap: '0.75rem' }}>
        <Card style={{ display: access.can('admin.users') ? undefined : 'none', padding: '1rem' }}>
          <div style={{ color: 'var(--color-text-secondary)', fontSize: '0.78rem', marginBottom: 6 }}>Accounts</div>
          <div style={{ fontSize: '1.3rem', fontWeight: 700 }}>{usersQuery.isLoading ? <Spinner /> : usersQuery.data?.length ?? 0}</div>
        </Card>
        <Card style={{ display: access.can('admin.system') ? undefined : 'none', padding: '1rem' }}>
          <div style={{ color: 'var(--color-text-secondary)', fontSize: '0.78rem', marginBottom: 6 }}>Service health</div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, fontWeight: 700 }}>

            {healthQuery.isLoading ? 'Checking…' : serviceHealthLabel}
          </div>
        </Card>
        <Card style={{ display: access.can('admin.system') ? undefined : 'none', padding: '1rem' }}>
          <div style={{ color: 'var(--color-text-secondary)', fontSize: '0.78rem', marginBottom: 6 }}>Services online</div>
          <div style={{ fontSize: '1.3rem', fontWeight: 700 }}>{stackQuery.isLoading ? <Spinner /> : `${servicesUp} / ${stackQuery.data?.services.length ?? 0}`}</div>
        </Card>
      </div>

    </div>
  )
}
