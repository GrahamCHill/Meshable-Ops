import { useState } from 'react'
import { notificationsApi, type DeliveryMethodView } from '../../../api/notifications'
import { getErrorMessage } from '../adminUtils'
import { Button } from '../../ui'
import { useQuery } from '@tanstack/react-query'
import api from '../../../api/http'

export function NotificationSendPanel({ methods, disabled }: { methods: DeliveryMethodView[]; disabled: boolean }) {
  const [methodId, setMethodId] = useState('email'); const [recipient, setRecipient] = useState(''); const [subject, setSubject] = useState(''); const [message, setMessage] = useState('')
  const [busy, setBusy] = useState(false); const [error, setError] = useState(''); const [notice, setNotice] = useState('')
  const selected = methods.find(value => value.method.id === methodId)
  const people = useQuery({queryKey: ['notification-send-members'], queryFn: () => api.get<{id: string; name: string}[]>('/admin/notifications/members').then(r => r.data), enabled: selected?.method.recipientKind === 'MEMBER'})
  const send = async () => { setBusy(true); setError(''); setNotice(''); try { const results = await notificationsApi.send({ methodId, recipient, subject, message, userId: selected?.method.recipientKind === 'MEMBER' ? recipient : undefined }); const result = results[methodId]; if (!result || result.status !== 'ACCEPTED') throw new Error(result?.message ?? 'This method is not selected for the recipient.'); setNotice(result.message); setMessage('') } catch (failure) { setError(getErrorMessage(failure, failure instanceof Error ? failure.message : 'Unable to send this message.')) } finally { setBusy(false) } }
  return <section className="page-stack"><h2>Send a message</h2><p>Uses the saved General messages template. Member delivery also follows the recipient's choices. Shared chat posts are available in the plugin's People & chats settings.</p>
    <form className="page-stack" onSubmit={event => {event.preventDefault(); void send()}}><fieldset className="page-stack" disabled={disabled || busy}>
      <label className="form-field">Delivery method<select value={methodId} onChange={event => {setMethodId(event.target.value); setRecipient('')}}>{methods.map(({method, enabled, available}) => <option value={method.id} key={method.id} disabled={!enabled || !available}>{method.label}{!enabled ? ' (disabled)' : !available ? ' (unavailable)' : ''}</option>)}</select></label>
      {selected?.method.recipientKind === 'MEMBER' ? <label className="form-field">Internal member<select required value={recipient} onChange={event => setRecipient(event.target.value)}><option value="">Select member</option>{people.data?.map(person => <option key={person.id} value={person.id}>{person.name}</option>)}</select></label> : selected?.method.recipientKind !== 'FIXED' && <label className="form-field">{selected?.method.recipientKind === 'PHONE' ? 'International phone number' : 'Recipient email'}<input type={selected?.method.recipientKind === 'EMAIL' ? 'email' : 'tel'} maxLength={254} required value={recipient} onChange={event => setRecipient(event.target.value)} /></label>}
      {selected?.method.recipientKind === 'EMAIL' && <label className="form-field">Subject<input maxLength={200} required value={subject} onChange={event => setSubject(event.target.value)} /></label>}
      <label className="form-field">Message<textarea rows={6} maxLength={selected?.method.maxTextLength ?? 4000} required value={message} onChange={event => setMessage(event.target.value)} /></label><div><Button type="submit" loading={busy} disabled={!selected?.enabled || !selected.available || !message.trim()}>Send message</Button></div>
    </fieldset></form>{error && <p role="alert">{error}</p>}{notice && <p role="status">{notice}</p>}
  </section>
}
