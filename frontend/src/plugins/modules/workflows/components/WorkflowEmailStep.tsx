import { useState } from 'react'
import { workflowRunsApi, type AdvanceInput, type WorkflowRun } from '../api'
import { useNotificationAvailability } from '../../../../api/notifications'
import { useDocumentAccess } from '../../../../api/documentAccess'
import type { RunAction } from './WorkflowRunPanel'
import { Button } from '../../../../components/ui/index'

export function WorkflowEmailStep({ run, busy, act, advance }: { run: WorkflowRun; busy: boolean; act: RunAction; advance: (input: Omit<AdvanceInput, 'revision' | 'nodeId'>) => Promise<void> }) {
  const block = run.current!; const policy = useDocumentAccess(); const notifications = useNotificationAvailability()
  const [password, setPassword] = useState(''); const [confirmed, setConfirmed] = useState(false); const [note, setNote] = useState('')
  const pending = run.emailState === 'SENDING' && (!run.emailAttemptAt || Date.now() - Date.parse(run.emailAttemptAt) < 120000)
  const protectedPdf = block.protection === 'PASSWORD'
  const passwordValid = !protectedPdf || password.length >= 12 && new TextEncoder().encode(password).length <= 72
  const send = async () => { const secret = password; setPassword(''); await act(() => workflowRunsApi.send(run.id, run.revision, block.id, secret, note)) }
  return <div className="page-stack">
    <dl><dt>Recipient</dt><dd>{block.recipient}</dd><dt>Subject</dt><dd>{block.subject}</dd><dt>Attachment</dt><dd>{protectedPdf ? 'Password protected PDF' : 'PDF'}</dd><dt>Message</dt><dd style={{ whiteSpace: 'pre-wrap' }}>{block.note || 'Please find the document attached.'}</dd></dl>
    <p className="field-help">Send attaches the PDF to this email. Continuing records the provider accepting it, which does not confirm inbox delivery.</p>
    {run.emailState === 'NONE' && !notifications.data?.workflowEmail && <p>Email delivery for workflow steps is disabled or unavailable. Contact an administrator or record delivery handled outside the app.</p>}
    {run.emailState === 'NONE' ? notifications.data?.workflowEmail && <form className="page-stack" onSubmit={e => { e.preventDefault(); void send() }}>
      {block.auditRequired && <label className="form-field">Required audit note<textarea required maxLength={1000} value={note} onChange={event => setNote(event.target.value)} disabled={busy}/></label>}
      {protectedPdf && <div className="form-field"><label htmlFor="workflow-pdf-password">PDF password</label><input id="workflow-pdf-password" type="password" autoComplete="new-password" minLength={12} maxLength={72} value={password} disabled={busy} onChange={e => setPassword(e.target.value)} /><p className="field-help">At least 12 characters, up to 72 UTF-8 bytes. The app does not save this password. Share it with the recipient separately.</p></div>}
      <div><Button type="submit" disabled={busy || !passwordValid || !policy.data || policy.data.downloadMode === 'DISABLED' || !run.documents.some(document => document.id === block.documentId)}>{busy ? 'Sending…' : 'Send PDF & continue'}</Button></div>
      {policy.data?.downloadMode === 'DISABLED' && <p>Email attachments are disabled by the document access policy.</p>}
    </form> : <p role="alert">{pending ? 'An email attempt is in progress. Reload to check its result.' : 'The previous email attempt has no confirmed result. Check the email outbox/provider before continuing. The app will not resend it automatically.'}</p>}
    <details><summary>Record delivery handled outside the app</summary><p className="field-help">Use this after checking the previous attempt, or when you have handled delivery separately.</p><form className="page-stack" onSubmit={e => { e.preventDefault(); void advance({ action: 'CONFIRM_EMAIL', note }) }}><div className="form-field"><label htmlFor="workflow-email-note">How was delivery handled?</label><textarea id="workflow-email-note" required maxLength={1000} value={note} disabled={busy || pending} onChange={e => setNote(e.target.value)} /></div><label className="checkbox-label"><input type="checkbox" required checked={confirmed} disabled={busy || pending} onChange={e => setConfirmed(e.target.checked)} />I checked delivery or handled it separately</label><div><Button variant="secondary" type="submit" disabled={busy || pending || !confirmed || !note.trim()}>Record confirmation & continue</Button></div></form></details>
  </div>
}
