import { useQuery } from '@tanstack/react-query'
import { eventsApi, type Event } from '../../api/client'
import { Card, Spinner, Badge } from '../ui'

const typeColors: Record<string, 'accent' | 'success' | 'warning' | 'danger' | 'default'> = {
  ITEM_CREATED: 'success',
  ITEM_DELETED: 'danger',
  FOLDER_CREATED: 'success',
  FOLDER_UPDATED: 'warning',
  FOLDER_DELETED: 'danger',
  SYSTEM_START: 'accent',
}

export function EventsPage() {
  const { data, isLoading } = useQuery({
    queryKey: ['events'],
    queryFn: () => eventsApi.list(),
    refetchInterval: 10_000,
  })

  return (
    <div style={{ maxWidth: 720 }}>
      <div style={{ marginBottom: '1.5rem' }}>
        <h1 style={{ fontSize: '1.4rem', fontWeight: 700, marginBottom: 4 }}>Event Log</h1>
        <p style={{ color: 'var(--color-text-secondary)', fontSize: '0.875rem' }}>
          Domain events stored in MongoDB · auto-refreshes every 10s
        </p>
      </div>
      {isLoading && <div style={{ display: 'flex', justifyContent: 'center', padding: '3rem' }}><Spinner /></div>}
      <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
        {data?.content.map(event => <EventRow key={event.id} event={event} />)}
        {data?.content.length === 0 && (
          <Card style={{ textAlign: 'center', padding: '2rem', color: 'var(--color-text-secondary)' }}>
            No events yet. Create or delete an item to generate events.
          </Card>
        )}
      </div>
      {data && (
        <div style={{ marginTop: '1rem', fontSize: '0.8rem', color: 'var(--color-text-tertiary)', textAlign: 'center' }}>
          Showing {data.content.length} of {data.totalElements} events
        </div>
      )}
    </div>
  )
}

function EventRow({ event }: { event: Event }) {
  return (
    <Card>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 12 }}>
        <div style={{ flex: 1 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 6 }}>
            <Badge color={typeColors[event.type] ?? 'default'}>{event.type}</Badge>
          </div>
          {Object.keys(event.payload).length > 0 && (
            <pre style={{ fontSize: '0.78rem', color: 'var(--color-text-secondary)', background: 'var(--color-bg)', padding: '0.5rem 0.75rem',  overflow: 'auto', margin: 0 }}>
              {JSON.stringify(event.payload, null, 2)}
            </pre>
          )}
        </div>
        <div style={{ fontSize: '0.75rem', color: 'var(--color-text-tertiary)', flexShrink: 0 }}>
          {event.createdAt ? new Date(event.createdAt).toLocaleString() : '—'}
        </div>
      </div>
    </Card>
  )
}
