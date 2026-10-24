import { useQuery } from '@tanstack/react-query'
import { notificationsApi } from '../../../api/notifications'
import { Button, Spinner } from '../../ui'

export function NotificationHistory() {
  const history = useQuery({ queryKey: ['notification-deliveries'], queryFn: notificationsApi.deliveries, refetchInterval: 10000 })
  return <section className="page-stack"><div className="inline-actions"><h2 style={{ flex: 1 }}>Delivery history</h2><Button variant="ghost" disabled={history.isFetching} onClick={() => void history.refetch()}>Refresh history</Button></div><p className="field-help">Latest 200 attempts, retained for 90 days. Acceptance does not confirm recipient delivery. Message bodies, codes, and full addresses are not stored here. Uncertain attempts are not retried automatically.</p>{history.isLoading && <Spinner />}{history.isError && <p role="alert">Unable to load delivery history.</p>}{history.data?.length === 0 && <p>No delivery attempts yet.</p>}<div className="notification-history" aria-label="Delivery history">{history.data?.map(value => <details key={value.id} className="notification-history-entry"><summary><strong>{value.notificationId}</strong> · {value.methodId} · {value.status.toLowerCase()}</summary><span>{value.recipientHint}</span><small>{new Date(value.createdAt).toLocaleString()}</small></details>)}</div></section>
}
