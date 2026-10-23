import { useQuery } from '@tanstack/react-query'
import { adminStackApi, healthApi } from '../../api/system'
import { Button, Card, Spinner } from '../ui'
import { AdminPageHeader } from '../admin/AdminPageHeader'
import { StackServiceCard } from '../admin/StackServiceCard'
import { formatDate } from '../admin/adminUtils'

function HealthValue({ label, value }: Readonly<{ label: string; value?: string }>) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 12, padding: '0.65rem 0' }}>
      <span>{label}</span>
      <span style={{ display: 'inline-flex', alignItems: 'center', gap: 7, fontWeight: 600 }}>{value ?? 'Checking…'}</span>
    </div>
  )
}

export function AdminSystemPage() {
  const stack = useQuery({ queryKey: ['admin-stack'], queryFn: adminStackApi.get, refetchInterval: 30_000 })
  const health = useQuery({ queryKey: ['admin-health'], queryFn: healthApi.check, refetchInterval: 30_000 })
  const refresh = () => { stack.refetch(); health.refetch() }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
      <AdminPageHeader
        title="System"
        description="Live health, network boundaries, and runtime details for the application stack. This information was moved here from Home."

        actions={<Button variant="secondary" size="sm" disabled={stack.isFetching || health.isFetching} onClick={refresh}> Refresh</Button>}
      />

      {(stack.isLoading || health.isLoading) ? <Card style={{ padding: '3rem', textAlign: 'center' }}><Spinner /></Card> : (
        <>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: '1rem' }}>
            <Card>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, fontWeight: 600, marginBottom: 6 }}> Dependency health</div>
              <HealthValue label="PostgreSQL" value={health.data?.postgres} />
              <HealthValue label="MongoDB" value={health.data?.mongodb} />
              <HealthValue label="pgvector" value={health.data?.pgvector} />
            </Card>
            <Card>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 10, fontWeight: 600 }}> Public entrypoints</div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 6, color: 'var(--color-text-secondary)', fontSize: '0.82rem' }}>{(stack.data?.publicEntrypoints ?? []).map(entry => <div key={entry}>{entry}</div>)}</div>
            </Card>
            <Card>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 10, fontWeight: 600 }}> Private services</div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 6, color: 'var(--color-text-secondary)', fontSize: '0.82rem' }}>{(stack.data?.privateServices ?? []).map(entry => <div key={entry}>{entry}</div>)}</div>
            </Card>
          </div>

          <Card style={{ padding: '0.85rem 1rem', color: 'var(--color-text-secondary)', fontSize: '0.8rem' }}>
            Runtime checked {formatDate(stack.data?.timestamp)} · Dependency health checked {formatDate(health.data?.timestamp)}
          </Card>

          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, fontWeight: 600, marginBottom: '0.75rem' }}> Service details</div>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))', gap: '0.75rem' }}>
              {(stack.data?.services ?? []).map(service => <StackServiceCard key={service.name} service={service} />)}
            </div>
          </div>
        </>
      )}
    </div>
  )
}
