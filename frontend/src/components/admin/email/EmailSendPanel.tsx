import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { emailApi } from '../../../api/email'
import { useAuthStore } from '../../../hooks/useAuthStore'
import { getErrorMessage } from '../adminUtils'
import { Button } from '../../ui'

export function EmailSendPanel({ disabled }: { disabled: boolean }) {
  const currentEmail = useAuthStore(state => state.user?.email ?? '')
  const [recipient, setRecipient] = useState(currentEmail)
  const [subject, setSubject] = useState('')
  const [text, setText] = useState('')
  const test = useMutation({ mutationFn: () => emailApi.test(recipient) })
  const send = useMutation({ mutationFn: () => emailApi.send({ recipient, subject, text }), onSuccess: () => { setSubject(''); setText('') } })
  const busy = test.isPending || send.isPending
  return <section className="page-stack">
    <h2 className="card-title">Send email</h2>
    <p className="field-help">Uses the saved configuration. Each button sends a real email to the address below. Provider acceptance does not confirm inbox delivery.</p>
    <form className="page-stack" onSubmit={event => { event.preventDefault(); test.reset(); send.mutate() }}>
      <fieldset className="page-stack" disabled={disabled || busy}>
        <div className="form-field"><label htmlFor="email-recipient">Recipient</label><input id="email-recipient" type="email" required maxLength={254} value={recipient} onChange={event => { setRecipient(event.target.value); test.reset(); send.reset() }} /></div>
        <div><Button type="button" variant="secondary" disabled={!recipient.trim()} loading={test.isPending} onClick={() => { send.reset(); test.mutate() }}>Send test email</Button></div>
        <div className="form-field"><label htmlFor="email-subject">Subject</label><input id="email-subject" required maxLength={200} value={subject} onChange={event => setSubject(event.target.value)} /></div>
        <div className="form-field"><label htmlFor="email-text">Message</label><textarea id="email-text" required rows={6} maxLength={20000} value={text} onChange={event => setText(event.target.value)} /><small>Plain text; one recipient per message.</small></div>
        <div><Button type="submit" disabled={!recipient.trim() || !subject.trim() || !text.trim()} loading={send.isPending}>Send email</Button></div>
      </fieldset>
    </form>
    {(test.isSuccess || send.isSuccess) && <p role="status" className="feedback success">{test.data?.message ?? send.data?.message}</p>}
    {(test.isError || send.isError) && <p role="alert" className="feedback error">{getErrorMessage(test.error ?? send.error)}</p>}
  </section>
}
