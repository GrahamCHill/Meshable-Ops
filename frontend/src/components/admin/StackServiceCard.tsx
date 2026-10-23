import { type StackServiceInfo } from '../../api/system'
import { Badge, Card } from '../ui'

function statusColor(status: string): 'success' | 'danger' | 'warning' {
  if (status === 'UP') return 'success'
  if (status === 'DOWN') return 'danger'
  return 'warning'
}

export function StackServiceCard({ service }: Readonly<{ service: StackServiceInfo }>) {
  return (
    <Card style={{ padding: '1rem' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, alignItems: 'flex-start', flexWrap: 'wrap', marginBottom: 10 }}>
        <div>
          <div style={{ fontWeight: 600 }}>{service.name}</div>
          {service.runtime && <div style={{ color: 'var(--color-text-secondary)', fontSize: '0.8rem', marginTop: 2 }}>{service.runtime}</div>}
        </div>
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', justifyContent: 'flex-end' }}>
          <Badge color={statusColor(service.status)}>{service.status}</Badge>
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
        <div style={{ color: 'var(--color-text-secondary)', fontSize: '0.8rem', lineHeight: 1.6 }}>{service.notes.join(' | ')}</div>
      )}
    </Card>
  )
}
