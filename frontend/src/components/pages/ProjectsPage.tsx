import { useEffect, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { projectsApi, emptyDetails } from '../../api/projects'
import { useAuthStore } from '../../hooks/useAuthStore'
import { ProjectEditor } from '../projects/ProjectEditor'
import { ClientsPanel } from '../projects/ClientsPanel'
import { WorkDigestPanel } from '../projects/WorkDigestPanel'
import { WorkSchedulesPanel } from '../projects/WorkSchedulesPanel'
import { Button } from '../ui'
import {ProjectSections} from '../projects/ProjectSections'
import '../../styles/projects.css'

export default function ProjectsPage() {
  const [params] = useSearchParams(); const client = params.get('client'); const user = useAuthStore(state => state.user); const navigate = useNavigate()
  const [tab, setTab] = useState('projects'); const [create, setCreate] = useState(false); const [query, setQuery] = useState(''); const [category, setCategory] = useState(''); const [tags, setTags] = useState<string[]>([])
  const projects = useQuery({queryKey: ['projects', user?.id, query, category, tags], queryFn: () => projectsApi.list(query, category, tags)})
  const options = useQuery({queryKey: ['project-options'], queryFn: projectsApi.options})
  useEffect(()=>{if(category&&options.data&&!options.data.sections.some(section=>section.id===category))setCategory('')},[category,options.data])
  return <div className="page-stack project-details"><div className="page-heading"><div><h1>Projects</h1><p>Clients, teams, delivery boards, and connected documents.</p></div><Button onClick={() => setCreate(true)} disabled={create || !options.data}>New project</Button></div>
    <nav className="inline-actions" aria-label="Project sections">{[['projects', 'Projects'], ['clients', 'Clients'], ['digest', 'My work digest'], ['schedules', 'My schedules']].map(([key, label]) => <Button key={key} variant={tab === key ? 'secondary' : 'ghost'} disabled={create} onClick={() => setTab(key)}>{label}</Button>)}</nav>
    {create ? <ProjectEditor initial={{name: '', leadId: user?.id ?? '', clientId: client, category: options.data?.defaultSectionId ?? 'ACTIVE', details: emptyDetails(), revision: -1}} onCancel={() => setCreate(false)} onSave={project => navigate(`/projects/${project.id}`)} /> : tab === 'clients' ? <ClientsPanel /> : tab === 'digest' ? <WorkDigestPanel /> : tab === 'schedules' ? <WorkSchedulesPanel /> : <>
      <div className="project-fields"><label className="form-field">Find projects<input type="search" value={query} maxLength={200} onChange={event => setQuery(event.target.value)} /></label><label className="form-field">Section<select value={category} onChange={event => setCategory(event.target.value)}><option value="">All sections</option>{options.data?.sections.map(section=><option key={section.id} value={section.id}>{section.name}</option>)}</select></label></div>
      <div className="project-choices" aria-label="Filter by tags">{options.data?.tags.map(tag => <label key={tag.id}><input type="checkbox" checked={tags.includes(tag.id)} onChange={event => setTags(event.target.checked ? [...tags, tag.id] : tags.filter(id => id !== tag.id))} />{tag.name}</label>)}</div>
      {client && <p>Showing this client's projects. <Link to="/projects">Clear client filter</Link></p>}{projects.isLoading && <p role="status">Loading projects…</p>}{projects.isError && <p role="alert">Unable to load projects. <Button variant="ghost" onClick={() => void projects.refetch()}>Retry</Button></p>}
      {options.isError&&<p role="alert">Unable to load project sections. <Button variant="ghost" onClick={()=>void options.refetch()}>Retry sections</Button></p>}{projects.isSuccess&&options.data&&<ProjectSections projects={projects.data.filter(project=>!client||project.clientId===client)} sections={options.data.sections} selected={category} tags={options.data.tags}/>}{projects.data?.length===200&&<p className="field-help">Showing up to 200 matching projects. Use filters to narrow the results.</p>}{projects.isSuccess&&!projects.data?.some(project=>!client||project.clientId===client)&&<p>No projects match these filters.</p>}
    </>}
  </div>
}
