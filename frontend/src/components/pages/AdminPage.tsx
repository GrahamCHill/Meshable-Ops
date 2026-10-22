import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { adminStackApi, adminUsersApi, healthApi } from '../../api/client'
import { Badge, Card, Spinner } from '../ui'
import { AdminPageHeader } from '../admin/AdminPageHeader'
import { Activity, ArrowRight, HardDrive, HeartPulse, LayoutGrid, Link2, ScrollText, Users, Workflow } from 'lucide-react'

const sections = [
  { to: '/admin/users', title: 'Users', description: 'Create accounts, assign roles, and deactivate access.', icon: Users },
  { to: '/admin/storage', title: 'Storage & vectors', description: 'Configure S3 or Azure Blob scope and vector maintenance.', icon: HardDrive },
  { to: '/admin/system', title: 'System', description: 'Inspect health, entrypoints, private services, and runtime details.', icon: HeartPulse },
  { to: '/admin/audit', title: 'Audit & backup', description: 'Review administrative activity and export app-managed data.', icon: ScrollText },
  { to: '/admin/dashboard-links', title: 'Dashboard links', description: 'Safely connect application views to Superset dashboards.', icon: Link2 },
  { to: '/admin/superset-graph-builder', title: 'Superset Builder', description: 'Open the protected dashboard authoring workspace.', icon: Workflow },
]

function statusColor(status?: string) {
  if (status === 'UP') return 'var(--color-success)'
  if (status === 'DOWN') return 'var(--color-danger)'
  return 'var(--color-warning)'
}

export function AdminPage() {
  const usersQuery = useQuery({ queryKey: ['admin-users'], queryFn: adminUsersApi.list })
  const healthQuery = useQuery({ queryKey: ['admin-health'], queryFn: healthApi.check, refetchInterval: 30_000 })
  const stackQuery = useQuery({ queryKey: ['admin-stack'], queryFn: adminStackApi.get, refetchInterval: 30_000 })
  const servicesUp = stackQuery.data?.services.filter(service => service.status === 'UP').length ?? 0
  const serviceHealthLabel = healthQuery.data?.postgres === 'UP' ? 'Operational' : 'Needs attention'

  return (
    <div style={{ width: '100%', display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
      <AdminPageHeader
        title="Administration"
        description="Manage access, storage, integrations, and operations from focused workspaces. Use System for the runtime information previously shown on Home."
        icon={LayoutGrid}
        actions={<Badge color="accent">Admin only</Badge>}
      />

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(210px, 1fr))', gap: '0.75rem' }}>
        <Card style={{ padding: '1rem' }}>
          <div style={{ color: 'var(--color-text-secondary)', fontSize: '0.78rem', marginBottom: 6 }}>Accounts</div>
          <div style={{ fontSize: '1.3rem', fontWeight: 700 }}>{usersQuery.isLoading ? <Spinner /> : usersQuery.data?.length ?? 0}</div>
        </Card>
        <Card style={{ padding: '1rem' }}>
          <div style={{ color: 'var(--color-text-secondary)', fontSize: '0.78rem', marginBottom: 6 }}>Service health</div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, fontWeight: 700 }}>
            <span style={{ width: 9, height: 9, borderRadius: '50%', background: statusColor(healthQuery.data?.postgres) }} />
            {healthQuery.isLoading ? 'Checking…' : serviceHealthLabel}
          </div>
        </Card>
        <Card style={{ padding: '1rem' }}>
          <div style={{ color: 'var(--color-text-secondary)', fontSize: '0.78rem', marginBottom: 6 }}>Services online</div>
          <div style={{ fontSize: '1.3rem', fontWeight: 700 }}>{stackQuery.isLoading ? <Spinner /> : `${servicesUp} / ${stackQuery.data?.services.length ?? 0}`}</div>
        </Card>
      </div>

      <div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, fontWeight: 600, marginBottom: '0.75rem' }}>
          <Activity size={16} /> Administration areas
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: '1rem' }}>
          {sections.map(({ to, title, description, icon: Icon }) => (
            <Link key={to} to={to} style={{ textDecoration: 'none', color: 'inherit' }}>
              <Card style={{ height: '100%', padding: '1.1rem', display: 'flex', gap: 12, alignItems: 'flex-start' }}>
                <div style={{ width: 38, height: 38, borderRadius: 11, background: 'var(--color-accent-muted)', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                  <Icon size={17} color="var(--color-accent)" />
                </div>
                <div style={{ flex: 1 }}>
                  <div style={{ fontWeight: 650, marginBottom: 5 }}>{title}</div>
                  <div style={{ color: 'var(--color-text-secondary)', fontSize: '0.82rem', lineHeight: 1.5 }}>{description}</div>
                </div>
                <ArrowRight size={15} color="var(--color-text-tertiary)" />
              </Card>
            </Link>
          ))}
        </div>
      </div>
    </div>
  )
}
