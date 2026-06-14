import { useQuery } from '@tanstack/react-query'
import { stackApi, type StackServiceInfo } from '../../api/client'
import { Card, Spinner, Badge } from '../ui'
import { useAuthStore } from '../../hooks/useAuthStore'
import { Globe, Lock, MonitorCog, RefreshCcw, Server } from 'lucide-react'

function statusBadgeColor(status: string): 'success' | 'danger' | 'warning' | 'default' {
  if (status === 'UP') return 'success'
  if (status === 'DOWN') return 'danger'
  return 'warning'
}

function StackServiceRow({ service }: { service: StackServiceInfo }) {
  return (
    <Card style={{ padding: '1rem' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, alignItems: 'flex-start', flexWrap: 'wrap', marginBottom: 10 }}>
        <div>
          <div style={{ fontWeight: 600 }}>{service.name}</div>
          {service.runtime && (
            <div style={{ color: 'var(--color-text-secondary)', fontSize: '0.8rem', marginTop: 2 }}>
              {service.runtime}
            </div>
          )}
        </div>
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', justifyContent: 'flex-end' }}>
          <Badge color={statusBadgeColor(service.status)}>{service.status}</Badge>
          <Badge color={service.exposedOutsideVm ? 'warning' : 'accent'}>
            {service.exposedOutsideVm ? 'Public/host reachable' : 'Internal only'}
          </Badge>
        </div>
      </div>

      {(service.version || service.internalUrl) && (
        <div style={{ color: 'var(--color-text-secondary)', fontSize: '0.82rem', lineHeight: 1.6, marginBottom: 8 }}>
          {service.version && <div>Version: <strong>{service.version}</strong></div>}
          {service.internalUrl && <div>Route: <strong>{service.internalUrl}</strong></div>}
        </div>
      )}

      {service.notes.length > 0 && (
        <div style={{ color: 'var(--color-text-secondary)', fontSize: '0.8rem', lineHeight: 1.6 }}>
          {service.notes.join(' | ')}
        </div>
      )}
    </Card>
  )
}

export function DashboardPage() {
  const { user } = useAuthStore()
  const stackQuery = useQuery({
    queryKey: ['stack'],
    queryFn: stackApi.get,
    refetchInterval: 30_000,
  })

  return (
    <div style={{ width: '100%', maxWidth: 'none', display: 'flex', flexDirection: 'column', gap: '1rem' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: '1rem', flexWrap: 'wrap' }}>
        <div>
          <h1 style={{ fontSize: '1.5rem', fontWeight: 700, marginBottom: 4 }}>
            Welcome back, {user?.name || user?.username}
          </h1>
          <p style={{ color: 'var(--color-text-secondary)', fontSize: '0.9rem' }}>
            Live system information for the currently running stack.
          </p>
        </div>
        <button
          onClick={() => stackQuery.refetch()}
          disabled={stackQuery.isFetching}
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: 8,
            padding: '0.7rem 0.95rem',
            borderRadius: 'var(--radius-md)',
            border: '1px solid var(--color-border)',
            background: 'var(--color-surface)',
            color: 'var(--color-text)',
          }}
        >
          <RefreshCcw size={15} />
          Refresh stack
        </button>
      </div>

      {stackQuery.isLoading ? (
        <Card style={{ display: 'flex', justifyContent: 'center', padding: '2rem' }}>
          <Spinner />
        </Card>
      ) : (
        <>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: '1rem' }}>
            <Card>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 10, fontWeight: 600 }}>
                <Globe size={16} /> Public entrypoints
              </div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 6, color: 'var(--color-text-secondary)', fontSize: '0.82rem' }}>
                {(stackQuery.data?.publicEntrypoints ?? []).map((entry) => (
                  <div key={entry}>{entry}</div>
                ))}
              </div>
            </Card>

            <Card>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 10, fontWeight: 600 }}>
                <Lock size={16} /> Private services
              </div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 6, color: 'var(--color-text-secondary)', fontSize: '0.82rem' }}>
                {(stackQuery.data?.privateServices ?? []).map((entry) => (
                  <div key={entry}>{entry}</div>
                ))}
              </div>
            </Card>
          </div>

          <Card>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8, fontWeight: 600 }}>
              <MonitorCog size={16} /> Runtime snapshot
            </div>
            <div style={{ color: 'var(--color-text-secondary)', fontSize: '0.82rem' }}>
              Checked {stackQuery.data?.timestamp ? new Date(stackQuery.data.timestamp).toLocaleString() : 'just now'}
            </div>
          </Card>

          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: '0.75rem', fontWeight: 600 }}>
              <Server size={16} /> Service details
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))', gap: '0.75rem' }}>
              {(stackQuery.data?.services ?? []).map((service) => (
                <StackServiceRow key={service.name} service={service} />
              ))}
            </div>
          </div>
        </>
      )}
    </div>
  )
}
