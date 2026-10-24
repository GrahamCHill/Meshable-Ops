import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { webhooksApi, type Webhook } from '../../api/webhooks'
import { Button, Spinner } from '../ui'
import { getErrorMessage } from './adminUtils'

export function WebhookHistory({ endpoint }: { endpoint: Webhook }) {
  const history = useQuery({ queryKey: ['webhook-history', endpoint.id], queryFn: () => webhooksApi.history(endpoint.id) })
  const [event, setEvent] = useState('manual.test')
  const [data, setData] = useState('{\n  "message": "Hello"\n}')
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState('')
  const send = async () => {
    setBusy(true); setMessage('')
    try {
      const payload: unknown = JSON.parse(data)
      const result = await webhooksApi.send(endpoint.id, event, payload)
      setMessage(result.status === 'ACCEPTED' ? `Accepted by destination (HTTP ${result.httpStatus}).` : `Delivery failed${result.httpStatus ? ` (HTTP ${result.httpStatus})` : ''}.`)
      await history.refetch()
    } catch (error) { setMessage(error instanceof SyntaxError ? 'Enter valid JSON.' : getErrorMessage(error, 'Unable to send webhook.')) }
    finally { setBusy(false) }
  }
  return <section className="page-stack">
    <h2>{endpoint.name}: deliveries</h2>
    {endpoint.direction === 'OUTGOING' && <form className="page-stack" onSubmit={e => { e.preventDefault(); void send() }}>
      <div className="form-field"><label htmlFor="webhook-event">Event name</label><input id="webhook-event" pattern="[A-Za-z0-9._-]{1,80}" required maxLength={80} value={event} onChange={e => setEvent(e.target.value)} /></div>
      <div className="form-field"><label htmlFor="webhook-data">JSON data</label><textarea id="webhook-data" rows={6} maxLength={60000} required value={data} onChange={e => setData(e.target.value)} /></div>
      <div><Button type="submit" loading={busy} disabled={!endpoint.enabled}>Send webhook</Button></div>
      {message && <p role="status">{message}</p>}
    </form>}
    <p className="field-help">The latest 100 deliveries are kept for up to seven days. Incoming JSON is received for review; it does not run actions.</p>
    <div><Button variant="ghost" onClick={() => history.refetch()}>Refresh deliveries</Button></div>
    {history.isLoading && <Spinner />}{history.isError && <p role="alert">Unable to load deliveries.</p>}
    {history.data?.length === 0 && <p>No deliveries yet.</p>}
    {history.data?.map(delivery => <details key={delivery.id}><summary>{new Date(delivery.createdAt).toLocaleString()} · {delivery.status}{delivery.httpStatus ? ` · HTTP ${delivery.httpStatus}` : ''}</summary><p>Delivery ID: {delivery.deliveryKey}</p><pre style={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>{delivery.payload}</pre></details>)}
  </section>
}
