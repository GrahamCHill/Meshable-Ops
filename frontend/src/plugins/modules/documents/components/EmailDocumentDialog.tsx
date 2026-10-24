import {useState} from 'react'
import {usePageDraft} from '../../../../hooks/usePageDraft'
import {useModalDialog} from '../../../../hooks/useModalDialog'
import { documentsApi, type EmailDocumentInput, type GeneratedDocument } from '../api'
import { getErrorMessage } from '../../../../components/admin/adminUtils'
import { Button } from '../../../../components/ui/index'

export function EmailDocumentDialog({ document, defaults, onClose }: { document: GeneratedDocument; defaults?: Partial<EmailDocumentInput>; onClose: () => void }) {
  const [input, setInput] = usePageDraft<EmailDocumentInput>('email-document:'+document.id, { recipient: '', subject: document.name, text: `Please find ${document.name} attached.`, protection: 'PLAIN', password: '', ...defaults })
  const [busy, setBusy] = useState(false); const [error, setError] = useState(''); const [notice, setNotice] = useState('')
  const dismiss = () => {setInput(current=>({...current,password:''}));onClose()}
  const dialog = useModalDialog(dismiss, busy)
  const send = async () => {
    setBusy(true); setError('')
    try { const result = await documentsApi.email(document.id, input); setInput(current => ({ ...current, password: '' })); setNotice(result.message) }
    catch (failure) { setError(getErrorMessage(failure, 'Unable to email the document.')) }
    finally { setBusy(false) }
  }
  return <dialog ref={dialog} className="pdf-viewer" style={{ maxWidth: 680 }} aria-labelledby="email-document-title" onCancel={e => { e.preventDefault(); if (!busy) dismiss() }}>
    <div className="pdf-toolbar"><h2 id="email-document-title">Email {document.name}</h2><Button variant="ghost" disabled={busy} onClick={dismiss}>Close</Button></div>
    {notice ? <p role="status">{notice}</p> : <form className="page-stack" onSubmit={e => { e.preventDefault(); void send() }}><fieldset disabled={busy} className="page-stack">
      <div className="form-field"><label htmlFor="document-email-to">Recipient email</label><input id="document-email-to" type="email" required maxLength={254} value={input.recipient} onChange={e => setInput({ ...input, recipient: e.target.value })} /></div>
      <div className="form-field"><label htmlFor="document-email-subject">Subject</label><input id="document-email-subject" required maxLength={200} value={input.subject} onChange={e => setInput({ ...input, subject: e.target.value })} /></div>
      <div className="form-field"><label htmlFor="document-email-text">Message</label><textarea id="document-email-text" rows={4} required maxLength={20000} value={input.text} onChange={e => setInput({ ...input, text: e.target.value })} /></div>
      <div className="form-field"><label htmlFor="document-email-protection">Attachment protection</label><select id="document-email-protection" value={input.protection} onChange={e => setInput({ ...input, protection: e.target.value as EmailDocumentInput['protection'], password: '' })}><option value="PLAIN">PDF without a password</option><option value="PASSWORD">Password-protected PDF</option></select></div>
      {input.protection === 'PASSWORD' && <div className="form-field"><label htmlFor="document-email-password">PDF password</label><input id="document-email-password" type="password" autoComplete="new-password" required minLength={12} maxLength={72} value={input.password} onChange={e => setInput({ ...input, password: e.target.value })} /><small>Communicate this separately. It is never saved with the workflow or automatically included in the email.</small></div>}
    </fieldset><p className="field-help">The PDF is attached to this email. Admin download/watermark policy applies. An emailed attachment cannot be recalled.</p>{error && <p role="alert" className="feedback error">{error}</p>}<div><Button type="submit" loading={busy}>Send PDF attachment</Button></div></form>}
  </dialog>
}
