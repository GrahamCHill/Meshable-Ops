import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { projectsApi, type DigestInput, type DigestPreview } from '../../api/projects'
import { getErrorMessage } from '../admin/adminUtils'
import { Button } from '../ui'

export function WorkDigestPanel({projectId}: {projectId?: string}) {
  const today = new Date(); const later = new Date(today); later.setDate(later.getDate() + 7)
  const [input, setInput] = useState<DigestInput>({from: today.toISOString().slice(0, 10), to: later.toISOString().slice(0, 10), timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone, includeAi: true, recipientIds: []})
  const [busy, setBusy] = useState(false); const [error, setError] = useState(''); const [notice, setNotice] = useState(''); const [preview, setPreview] = useState<DigestPreview>()
  const members = useQuery({queryKey: ['project-members', projectId], queryFn: () => projectsApi.members(projectId!), enabled: !!projectId})
  const run = async (send: boolean) => {setBusy(true); setError(''); setNotice(''); try {
    if (send) {const result = await projectsApi.sendDigest(projectId, input); const values = projectId ? Object.values(result) as {status: string}[] : [result as {status: string}]; setNotice('Email result: ' + values.map(value => value.status).join(', '))}
    else setPreview(await projectsApi.previewDigest(input))
  } catch (failure) {setError(getErrorMessage(failure, 'Unable to prepare the work digest.'))} finally {setBusy(false)}}
  return <section className="page-stack"><h2>{projectId ? 'Email team work digests' : 'My work digest'}</h2><p>Each recipient receives their in-progress assignments, due or overdue work, and their own calendar commitments. Private calendar details are never returned to someone triggering another member's email.</p>
    <form className="page-stack" onSubmit={event => {event.preventDefault(); void run(true)}}><fieldset className="page-stack" disabled={busy}><div className="project-fields"><label className="form-field">From<input type="date" required value={input.from} onChange={event => setInput({...input, from: event.target.value})} /></label><label className="form-field">Through<input type="date" required min={input.from} value={input.to} onChange={event => setInput({...input, to: event.target.value})} /></label><label className="form-field">Timezone<input required value={input.timeZone} onChange={event => setInput({...input, timeZone: event.target.value})} /></label></div><label><input type="checkbox" checked={input.includeAi} onChange={event => setInput({...input, includeAi: event.target.checked})} />Include an optional AI summary when a configured model is available</label><p className="field-help">If AI is disabled or fails, the factual digest is still prepared. When enabled, the configured AI provider receives this recipient's selected work and calendar facts.</p>
    {projectId && <div className="project-choices">{members.data?.map(person => <label key={person.id}><input type="checkbox" checked={input.recipientIds.includes(person.id)} onChange={event => setInput({...input, recipientIds: event.target.checked ? [...input.recipientIds, person.id] : input.recipientIds.filter(id => id !== person.id)})} />{person.name}</label>)}</div>}
    <div className="inline-actions">{!projectId && <Button type="button" variant="secondary" onClick={() => void run(false)}>Preview my digest</Button>}<Button type="submit" loading={busy} disabled={!!projectId && !input.recipientIds.length}>Send email{projectId ? 's' : ' to me'}</Button></div></fieldset></form>
    {error && <p role="alert">{error}</p>}{notice && <p role="status">{notice}</p>}{preview && <div className="page-stack">{preview.aiSummary && <><h3>Optional AI summary</h3><p className="project-note">{preview.aiSummary}</p></>}<pre className="project-note">{preview.text}</pre></div>}
  </section>
}
