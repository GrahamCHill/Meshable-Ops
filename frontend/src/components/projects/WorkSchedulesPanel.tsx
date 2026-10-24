import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { projectsApi, type ScheduleInput, type WorkSchedule } from '../../api/projects'
import { getErrorMessage } from '../admin/adminUtils'
import { Button } from '../ui'

export function WorkSchedulesPanel({projectId}: {projectId?: string}) {
  const client = useQueryClient(); const key = ['work-schedules', projectId]
  const schedules = useQuery({queryKey: key, queryFn: () => projectsApi.schedules(projectId)})
  const members = useQuery({queryKey: ['project-members', projectId], queryFn: () => projectsApi.members(projectId!), enabled: !!projectId})
  const [draft, setDraft] = useState<ScheduleInput>(); const [id, setId] = useState<string>(); const [busy, setBusy] = useState(false); const [error, setError] = useState('')
  const edit = (value?: WorkSchedule) => {setId(value?.id); setError(''); setDraft(value?.configuration ?? {enabled: false, kind: projectId ? 'REPORT' : 'DIGEST', cadence: projectId ? 'MONTHLY' : 'DAILY', time: '08:00', timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone, dayOfWeek: 1, dayOfMonth: 1, reportKind: 'MONTHLY', recipientIds: [], includeAi: true, revision: -1})}
  const save = async () => {if (!draft) return; setBusy(true); setError(''); try {await projectsApi.saveSchedule(projectId, id, draft); setDraft(undefined); await client.invalidateQueries({queryKey: key})} catch (failure) {setError(getErrorMessage(failure))} finally {setBusy(false)}}
  return <section className="page-stack"><div className="inline-actions"><h2>Schedules</h2><Button onClick={() => edit()} disabled={!!draft}>New schedule</Button></div><p>Manual until configured. New schedules start disabled. Project reports always include the project lead when emailed. Sprint schedules pick the latest newly completed sprint.</p>
    {schedules.isError && <p role="alert">Unable to load schedules.</p>}
    {!draft && schedules.data?.map(item => <div className="inline-actions" key={item.id}><span>{item.configuration.kind.toLowerCase()} · {item.configuration.cadence.toLowerCase()} · {item.configuration.time} {item.configuration.timeZone} · {item.configuration.enabled ? 'Enabled' : 'Disabled'}{item.nextRunAt ? ` · Next ${new Date(item.nextRunAt).toLocaleString()}` : ''}{item.lastStatus ? ` · ${item.lastStatus}` : ''}</span><Button variant="ghost" onClick={() => edit(item)}>Edit schedule</Button></div>)}
    {draft && <form className="page-stack" onSubmit={event => {event.preventDefault(); void save()}}><fieldset className="page-stack" disabled={busy}>
      <label><input type="checkbox" checked={draft.enabled} onChange={event => setDraft({...draft, enabled: event.target.checked})} />Enable automatic email delivery</label><div className="project-fields">
      {projectId && <label className="form-field">Send<select value={draft.kind} onChange={event => setDraft({...draft, kind: event.target.value as ScheduleInput['kind']})}><option value="REPORT">Project PDF report</option><option value="DIGEST">Individual member work digests</option></select></label>}
      {draft.kind === 'REPORT' && <label className="form-field">Report scope<select value={draft.reportKind} onChange={event => setDraft({...draft, reportKind: event.target.value as ScheduleInput['reportKind']})}><option value="MONTHLY">Previous calendar month</option><option value="SPRINT">Latest completed sprint</option><option value="PERIOD">Previous seven days</option></select></label>}
      <label className="form-field">Repeat<select value={draft.cadence} onChange={event => setDraft({...draft, cadence: event.target.value as ScheduleInput['cadence']})}><option value="DAILY">Daily</option><option value="WEEKLY">Weekly</option><option value="MONTHLY">Monthly</option></select></label>
      {draft.cadence === 'WEEKLY' && <label className="form-field">Weekday<select value={draft.dayOfWeek} onChange={event => setDraft({...draft, dayOfWeek: Number(event.target.value)})}>{['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'].map((day, i) => <option key={day} value={i + 1}>{day}</option>)}</select></label>}
      {draft.cadence === 'MONTHLY' && <label className="form-field">Day of month<input required type="number" min={1} max={28} value={draft.dayOfMonth} onChange={event => setDraft({...draft, dayOfMonth: Number(event.target.value)})} /></label>}
      <label className="form-field">Time<input type="time" required value={draft.time} onChange={event => setDraft({...draft, time: event.target.value})} /></label><label className="form-field">Timezone<input required value={draft.timeZone} onChange={event => setDraft({...draft, timeZone: event.target.value})} /></label></div>
      {draft.kind === 'DIGEST' && <label><input type="checkbox" checked={draft.includeAi} onChange={event => setDraft({...draft, includeAi: event.target.checked})} />Optional AI summary when configured</label>}
      {projectId && <><h3>{draft.kind === 'REPORT' ? 'Additional internal recipients' : 'Internal digest recipients'}</h3><div className="project-choices">{members.data?.map(person => <label key={person.id}><input type="checkbox" checked={draft.recipientIds.includes(person.id)} onChange={event => setDraft({...draft, recipientIds: event.target.checked ? [...draft.recipientIds, person.id] : draft.recipientIds.filter(id => id !== person.id)})} />{person.name}</label>)}</div></>}
      <div className="inline-actions"><Button type="submit" loading={busy}>Save schedule</Button><Button type="button" variant="ghost" onClick={() => setDraft(undefined)}>Cancel</Button></div>
    </fieldset></form>}{error && <p role="alert">{error}</p>}
  </section>
}
