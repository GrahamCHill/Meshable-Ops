import {useState} from 'react'
import {usePageDraft} from '../../hooks/usePageDraft'
import {useModalDialog} from '../../hooks/useModalDialog'
import { sharesApi } from '../../api/shares'
import { getErrorMessage } from '../admin/adminUtils'
import { Button } from '../ui'

export function SharePdfDialog({ folderId, path, maxLifetimeHours, onClose }: { folderId: string; path: string; maxLifetimeHours: number; onClose: () => void }) {
  const [recipient, setRecipient] = usePageDraft('share-recipient:'+folderId+':'+path, '')
  const [password, setPassword] = useState('')
  const [hours, setHours] = usePageDraft('share-hours:'+folderId+':'+path, Math.min(72, maxLifetimeHours))
  const [url, setUrl] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const dialog = useModalDialog(onClose, busy)
  const send = async () => {
    setBusy(true); setError('')
    try {
      const created = await sharesApi.create({ folderId, path, recipient, password, lifetimeHours: hours })
      setPassword(''); setUrl(created.url)
    } catch (failure) { setError(getErrorMessage(failure, 'Unable to email this PDF.')) }
    finally { setBusy(false) }
  }
  return <dialog ref={dialog} className="pdf-viewer" style={{ maxWidth: 640 }} aria-labelledby="share-title" onCancel={event => { event.preventDefault(); if (!busy) onClose() }}>
    <div className="pdf-toolbar"><h2 id="share-title">Email protected PDF</h2><Button variant="ghost" disabled={busy} onClick={onClose}>Close</Button></div>
    <p>{path.split('/').pop()}</p>
    {url ? <div className="page-stack" role="status"><p>The email provider accepted the message with a watermarked, password-protected PDF attached. Communicate the password separately.</p><p>The email also contains this protected link:</p><code style={{ overflowWrap: 'anywhere' }}>{url}</code><p className="field-help">You can revoke the online link in Admin → File sharing. An emailed attachment cannot be revoked or expired.</p></div> : <form className="page-stack" onSubmit={e => { e.preventDefault(); void send() }}>
      <p>A watermarked, password-protected PDF will be attached. The password is kept out of the email.</p>
      <fieldset disabled={busy} className="page-stack">
        <div className="form-field"><label htmlFor="share-email">Recipient email</label><input id="share-email" type="email" required maxLength={254} value={recipient} onChange={e => setRecipient(e.target.value)} autoFocus /></div>
        <div className="form-field"><label htmlFor="share-password">PDF and link password</label><input id="share-password" type="password" autoComplete="new-password" required minLength={12} maxLength={72} value={password} onChange={e => setPassword(e.target.value)} /><small>Use at least 12 characters. Give this password to the recipient through a separate channel.</small></div>
        <div className="form-field"><label htmlFor="share-hours">Online link lifetime (hours)</label><input id="share-hours" type="number" min={1} max={maxLifetimeHours} required value={hours} onChange={e => setHours(Number(e.target.value))} /><small>The link expires; the attached PDF remains accessible with its password.</small></div>
      </fieldset>
      <p className="field-help">PDF only, up to 10 MB after protection. The storage original stays unchanged.</p>
      {error && <p role="alert" className="feedback error">{error}</p>}
      <div><Button type="submit" loading={busy}>Email PDF attachment</Button></div>
    </form>}
  </dialog>
}
