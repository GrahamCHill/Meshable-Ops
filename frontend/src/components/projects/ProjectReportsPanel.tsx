import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { projectsApi, downloadProjectBlob, type Project, type ReportInput } from '../../api/projects'
import { getErrorMessage } from '../admin/adminUtils'
import { Button } from '../ui'
import { boardsApi } from '../../plugins/modules/kanban/api'
import { useAvailablePlugins } from '../../api/plugins'
import { Link } from 'react-router-dom'
import { useAccess } from '../../api/access'

export function ProjectReportsPanel({project}: {project: Project}) {
  const access = useAccess()
  const today = new Date().toISOString().slice(0, 10); const client = useQueryClient()
  const [input, setInput] = useState<ReportInput>({kind: 'MONTHLY', from: today.slice(0, 8) + '01', to: today, sprintId: null, boardId: null, sendEmail: false, recipientIds: []})
  const [busy, setBusy] = useState(false); const [error, setError] = useState(''); const [notice, setNotice] = useState('')
  const reports = useQuery({queryKey: ['project-reports', project.id], queryFn: () => projectsApi.reports(project.id)})
  const sprints = useQuery({queryKey: ['project-sprints', project.id], queryFn: () => projectsApi.sprints(project.id)})
  const members = useQuery({queryKey: ['project-members', project.id], queryFn: () => projectsApi.members(project.id)})
  const plugins = useAvailablePlugins()
  const boards = useQuery({queryKey: ['project-report-boards', project.id], queryFn: () => boardsApi.list(project.id), enabled: plugins.data?.some(plugin => plugin.manifest.id === 'kanban') === true})
  const run = async (action: () => Promise<void>) => {setBusy(true); setError(''); setNotice(''); try {await action()} catch (failure) {setError(getErrorMessage(failure, 'Unable to generate or download this report.'))} finally {setBusy(false)}}
  return <section className="page-stack"><h2>Project reports</h2><p>Generate a report for a sprint, month, or selected period. The shared cover and index come first, followed by LLM-summarised insights when enabled in Administration, then Kanban summary, additional insights, sprint monitor, ticket lead times and the ticket export. Reports use current ticket data and bounded table excerpts. Delivery-risk scores and private calendars are excluded.</p>
    {access.can('admin.projects') && <p><Link to="/admin/projects/report-covers">Redesign report covers</Link></p>}
    <p className="field-help">Reporting project: <strong>{project.name}</strong>. Reports include only this project's boards and tickets.</p>
    <form className="page-stack" onSubmit={event => {event.preventDefault(); void run(async () => {const result = await projectsApi.report(project.id, input); await client.invalidateQueries({queryKey: ['project-reports', project.id]}); setNotice(`Report generated.${input.sendEmail ? ' Email: ' + Object.values(result.deliveries).map(item => item.status).join(', ') : ''}`)})}}><fieldset disabled={busy} className="page-stack">
      <div className="project-fields"><label className="form-field">Report type<select value={input.kind} onChange={event => setInput({...input, kind: event.target.value as ReportInput['kind'], sprintId: null, boardId: null})}><option value="MONTHLY">Monthly</option><option value="SPRINT">Sprint</option><option value="PERIOD">Specific period</option></select></label>{input.kind === 'SPRINT' && <label className="form-field">Sprint<select required value={input.sprintId ?? ''} onChange={event => {const selected = sprints.data?.find(item => item.sprint.id === event.target.value); setInput({...input, sprintId: selected?.sprint.id ?? null, boardId: selected?.boardId ?? null, from: selected?.sprint.startDate ?? input.from, to: selected?.sprint.endDate ?? input.to})}}><option value="">Choose a sprint</option>{sprints.data?.map(item => <option key={`${item.boardId}-${item.sprint.id}`} value={item.sprint.id}>{item.boardName} · {item.sprint.name}</option>)}</select></label>}<label className="form-field">From<input type="date" required value={input.from} onChange={event => setInput({...input, from: event.target.value})} /></label><label className="form-field">Through<input type="date" required min={input.from} value={input.to} onChange={event => setInput({...input, to: event.target.value})} /></label></div>
      {input.kind !== 'SPRINT' && !!boards.data?.length && <label className="form-field">Board scope<select value={input.boardId ?? ''} onChange={event => setInput({...input, boardId: event.target.value || null})}><option value="">All accessible boards in this project</option>{boards.data.map(board => <option key={board.id} value={board.id}>{board.name}</option>)}</select></label>}
      {project.canManage && <><label><input type="checkbox" checked={input.sendEmail} onChange={event => setInput({...input, sendEmail: event.target.checked})} /> Email the generated PDF</label>{input.sendEmail && <><p>The project lead is always included. Other recipients must be internal members who can access every included board.</p><div className="project-choices">{members.data?.map(person => <label key={person.id}><input type="checkbox" disabled={person.id === project.leadId} checked={person.id === project.leadId || input.recipientIds.includes(person.id)} onChange={event => setInput({...input, recipientIds: event.target.checked ? [...input.recipientIds, person.id] : input.recipientIds.filter(id => id !== person.id)})} />{person.name}{person.id === project.leadId ? ' (project lead)' : ''}</label>)}</div></>}</>}
      <div><Button type="submit" loading={busy} disabled={input.kind === 'SPRINT' && !input.sprintId}>Generate report</Button></div>
    </fieldset></form>{error && <p role="alert">{error}</p>}{notice && <p role="status">{notice}</p>}
    {reports.isError && <p role="alert">Unable to list reports.</p>}{reports.isLoading && <p role="status">Loading reports…</p>}
    <div className="project-table-wrap"><table className="project-table"><thead><tr><th>Type</th><th>Period</th><th>Created</th><th>PDF</th></tr></thead><tbody>{reports.data?.map(report => <tr key={report.id}><td>{report.kind.toLowerCase()}</td><td>{report.from} — {report.to}</td><td>{new Date(report.createdAt).toLocaleString()}</td><td><Button variant="ghost" disabled={busy} onClick={() => void run(async () => downloadProjectBlob(await projectsApi.pdf(project.id, report.id), `project-report-${report.id}.pdf`))}>Download PDF</Button></td></tr>)}</tbody></table></div>
  </section>
}
