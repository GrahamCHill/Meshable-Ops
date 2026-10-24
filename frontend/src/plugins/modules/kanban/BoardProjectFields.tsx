import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { projectsApi } from '../../../api/projects'
import { useAccess } from '../../../api/access'
import type { BoardInput } from './api'

export function BoardProjectFields({draft, onChange, disabled}: {draft: BoardInput; onChange: (next: BoardInput) => void; disabled: boolean}) {
  const access = useAccess()
  const projects = useQuery({queryKey: ['board-project-options'], queryFn: () => projectsApi.list('', ''), enabled: access.can('projects')})
  const options = useQuery({queryKey: ['project-options'], queryFn: projectsApi.options, enabled: access.can('projects')})
  const project = useQuery({queryKey: ['project', draft.projectId], queryFn: () => projectsApi.get(draft.projectId!), enabled: !!draft.projectId && access.can('projects')})
  if (!access.can('projects')) return null
  const restricted = disabled || !!draft.projectId && project.data?.canManage !== true
  return <section className="page-stack"><label className="form-field">Linked project<select disabled={restricted} value={draft.projectId ?? ''} onChange={event => onChange({...draft, projectId: event.target.value || null, teamIds: []})}><option value="">Private board (owner and administrators)</option>{projects.data?.map(project => <option key={project.id} value={project.id}>{project.name} · {project.clientName}</option>)}</select></label>
    {draft.projectId && <><Link to={`/projects/${draft.projectId}`}>Open project</Link><p className="field-help">Without a team restriction, project members can access this board. Choose teams to isolate their work within the project; its lead and managers retain oversight.</p><div className="project-choices">{options.data?.groups.filter(group => project.data?.details.groupIds.includes(group.id)).map(group => <label key={group.id}><input type="checkbox" disabled={restricted} checked={draft.teamIds?.includes(group.id) ?? false} onChange={event => onChange({...draft, teamIds: event.target.checked ? [...(draft.teamIds ?? []), group.id] : draft.teamIds?.filter(id => id !== group.id)})} />{group.name}</label>)}</div></>}
  </section>
}
