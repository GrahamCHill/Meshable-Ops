import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { webhooksApi, type Webhook, type WebhookInput } from '../../api/webhooks'
import { AdminPageHeader } from '../admin/AdminPageHeader'
import { WebhookHistory } from '../admin/WebhookHistory'
import { getErrorMessage } from '../admin/adminUtils'
import { Button, Card, Spinner } from '../ui'

const empty = (): WebhookInput => ({ name: '', direction: 'INCOMING', enabled: true, url: '', authentication: 'HMAC', secret: '' })
export function AdminWebhooksPage() {
  const client = useQueryClient()
  const list = useQuery({ queryKey: ['admin-webhooks'], queryFn: webhooksApi.list })
  const [id, setId] = useState<string>()
  const [draft, setDraft] = useState<WebhookInput>(empty)
  const [selected, setSelected] = useState<Webhook>()
  const [secret, setSecret] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const edit = (endpoint?: Webhook) => { setId(endpoint?.id); setDraft(endpoint ? { ...endpoint, url: endpoint.direction === 'OUTGOING' ? endpoint.url : '', secret: '' } : empty()); setSecret(''); setError(''); setSelected(endpoint) }
  const save = async () => {
    setBusy(true); setError(''); setSecret('')
    try {
      const result = await webhooksApi.save(id, { ...draft, secret: draft.secret || undefined })
      setSecret(result.secret ?? ''); setId(result.endpoint.id); setSelected(result.endpoint)
      setDraft({ ...result.endpoint, url: result.endpoint.direction === 'OUTGOING' ? result.endpoint.url : '', secret: '' })
      await client.invalidateQueries({ queryKey: ['admin-webhooks'] })
    } catch (failure) { setError(getErrorMessage(failure, 'Unable to save webhook.')) }
    finally { setBusy(false) }
  }
  return <div className="page-stack" style={{ maxWidth: 900 }}>
    <AdminPageHeader title="Webhooks" description="Send JSON to another service or receive signed requests for review." />
    <p className="field-help">Set the server encryption key before creating endpoints. Payloads are encrypted and visible only to admins. Sends are manual; incoming requests do not trigger app actions.</p>
    {list.isLoading && <Spinner />}{list.isError && <p role="alert">Unable to load webhooks. <Button variant="ghost" onClick={() => list.refetch()}>Retry</Button></p>}
    <div className="inline-actions"><Button variant="secondary" onClick={() => edit()}>New webhook</Button></div>
    {list.data?.map(endpoint => <Card key={endpoint.id}><div className="inline-actions"><span style={{ flex: 1 }}>{endpoint.name} · {endpoint.direction === 'INCOMING' ? 'Receive' : 'Send'} · {endpoint.enabled ? 'Enabled' : 'Disabled'}</span><Button variant="ghost" onClick={() => edit(endpoint)}>Manage</Button></div></Card>)}
    <form className="page-stack" onSubmit={e => { e.preventDefault(); void save() }}>
      <h2>{id ? 'Edit webhook' : 'New webhook'}</h2>
      <fieldset disabled={busy} className="page-stack">
        <div className="form-field"><label htmlFor="hook-name">Name</label><input id="hook-name" required maxLength={120} value={draft.name} onChange={e => setDraft({ ...draft, name: e.target.value })} /></div>
        <div className="form-grid"><div className="form-field"><label htmlFor="hook-direction">Direction</label><select id="hook-direction" disabled={!!id} value={draft.direction} onChange={e => setDraft({ ...draft, direction: e.target.value as WebhookInput['direction'] })}><option value="INCOMING">Receive</option><option value="OUTGOING">Send</option></select></div><div className="form-field"><label htmlFor="hook-auth">Authentication</label><select id="hook-auth" value={draft.authentication} onChange={e => setDraft({ ...draft, authentication: e.target.value as WebhookInput['authentication'] })}><option value="HMAC">HMAC signature (SHA-256)</option><option value="BEARER">Bearer secret</option></select></div></div>
        <label className="checkbox-label"><input type="checkbox" checked={draft.enabled} onChange={e => setDraft({ ...draft, enabled: e.target.checked })} /> Enable webhook</label>
        {draft.direction === 'OUTGOING' && <div className="form-field"><label htmlFor="hook-url">Destination URL</label><input id="hook-url" type="url" required maxLength={2000} placeholder="https://example.com/webhooks" value={draft.url} onChange={e => setDraft({ ...draft, url: e.target.value })} /><small>Public HTTPS destinations by default. Changing the URL generates a new secret unless you supply one.</small></div>}
        <div className="form-field"><label htmlFor="hook-secret">Secret (optional)</label><input id="hook-secret" type="password" autoComplete="new-password" minLength={32} maxLength={4096} value={draft.secret ?? ''} onChange={e => setDraft({ ...draft, secret: e.target.value })} /><small>Leave blank to generate a secret for a new endpoint, or retain an existing secret. Saved secrets are encrypted and never returned.</small></div>
        {id && <label className="checkbox-label"><input type="checkbox" checked={!!draft.rotateSecret} onChange={e => setDraft({ ...draft, rotateSecret: e.target.checked })} /> Generate a replacement secret</label>}
      </fieldset>
      {error && <p role="alert" className="feedback error">{error}</p>}
      <div><Button type="submit" loading={busy}>Save webhook</Button></div>
    </form>
    {secret && <div className="page-stack" role="status"><p>Copy this secret now. It will only be shown once.</p><code style={{ overflowWrap: 'anywhere' }}>{secret}</code><div><Button variant="ghost" onClick={() => setSecret('')}>Hide secret</Button></div></div>}
    {selected?.direction === 'INCOMING' && <section className="page-stack"><h2>Receiving requests</h2><p>POST JSON to <code style={{ overflowWrap: 'anywhere' }}>{selected.url}</code></p>{selected.authentication === 'HMAC' ? <p>Send X-Webhook-Id (unique delivery ID), X-Webhook-Timestamp (Unix seconds), and X-Webhook-Signature as <code>sha256=HMAC_SHA256(secret, timestamp + "." + deliveryId + "." + rawBody)</code>. Requests must be within five minutes; repeated delivery IDs are acknowledged once.</p> : <p>Send <code>Authorization: Bearer &lt;secret&gt;</code>. Include a unique X-Webhook-Id to deduplicate retries.</p>}</section>}
    {selected && <WebhookHistory key={selected.id} endpoint={selected} />}
  </div>
}
