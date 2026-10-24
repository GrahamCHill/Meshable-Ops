import { useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { projectsApi, emptyDetails } from '../../api/projects'
import { useAuthStore } from '../../hooks/useAuthStore'
import { ProjectEditor } from '../projects/ProjectEditor'
import { ClientsPanel } from '../projects/ClientsPanel'
import { WorkDigestPanel } from '../projects/WorkDigestPanel'
import { WorkSchedulesPanel } from '../projects/WorkSchedulesPanel'
import { Button } from '../ui'
import '../../styles/projects.css'

export default function ProjectsPage() {
  const [params] = useSearchParams(); const client = params.get('client'); const user = useAuthStore(state => state.user); const navigate = useNavigate()
  const [tab, setTab] = useState('projects'); const [create, setCreate] = useState(false); const [query, setQuery] = useState(''); const [category, setCategory] = useState('ACTIVE'); const [tags, setTags] = useState<string[]>([])
  const projects = useQuery({queryKey: ['projects', user?.id, query, category, tags], queryFn: () => projectsApi.list(query, category, tags)})
  const options = useQuery({queryKey: ['project-options'], queryFn: projectsApi.options})
  return <div className="page-stack project-details"><div className="page-heading"><div><h1>Projects</h1><p>Clients, teams, delivery boards, and connected documents.</p></div><Button onClick={() => setCreate(true)} disabled={create}>New project</Button></div>
    <nav className="inline-actions" aria-label="Project sections">{[['projects', 'Projects'], ['clients', 'Clients'], ['digest', 'My work digest'], ['schedules', 'My schedules']].map(([key, label]) => <Button key={key} variant={tab === key ? 'secondary' : 'ghost'} disabled={create} onClick={() => setTab(key)}>{label}</Button>)}</nav>
    {create ? <ProjectEditor initial={{name: '', leadId: user?.id ?? '', clientId: client, category: 'ACTIVE', details: emptyDetails(), revision: -1}} onCancel={() => setCreate(false)} onSave={project => navigate(`/projects/${project.id}`)} /> : tab === 'clients' ? <ClientsPanel /> : tab === 'digest' ? <WorkDigestPanel /> : tab === 'schedules' ? <WorkSchedulesPanel /> : <>
      <div className="project-fields"><label className="form-field">Find projects<input type="search" value={query} maxLength={200} onChange={event => setQuery(event.target.value)} /></label><label className="form-field">Category<select value={category} onChange={event => setCategory(event.target.value)}><option value="">All categories</option><option value="ACTIVE">Active</option><option value="PROPOSAL">Proposals</option><option value="ARCHIVED">Archived</option></select></label></div>
      <div className="project-choices" aria-label="Filter by tags">{options.data?.tags.map(tag => <label key={tag.id}><input type="checkbox" checked={tags.includes(tag.id)} onChange={event => setTags(event.target.checked ? [...tags, tag.id] : tags.filter(id => id !== tag.id))} />{tag.name}</label>)}</div>
      {client && <p>Showing this client's projects. <Link to="/projects">Clear client filter</Link></p>}{projects.isLoading && <p role="status">Loading projects…</p>}{projects.isError && <p role="alert">Unable to load projects. <Button variant="ghost" onClick={() => void projects.refetch()}>Retry</Button></p>}
      <div className="project-table-wrap"><table className="project-table"><thead><tr><th>Project</th><th>Client / identity</th><th>Category</th><th>Tags</th><th>Updated</th></tr></thead><tbody>{projects.data?.filter(project => !client || project.clientId === client).map(project => <tr key={project.id}><td><Link to={`/projects/${project.id}`}>{project.name}</Link></td><td>{project.clientName}</td><td>{project.category.toLowerCase()}</td><td>{project.tagIds.map(id => options.data?.tags.find(tag => tag.id === id)?.name).filter(Boolean).join(', ')}</td><td>{new Date(project.updatedAt).toLocaleDateString()}</td></tr>)}</tbody></table></div>{projects.data?.length === 0 && <p>No projects match these filters.</p>}
    </>}
  </div>
}
