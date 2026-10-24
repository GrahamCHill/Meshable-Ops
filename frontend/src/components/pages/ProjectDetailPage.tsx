import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { projectsApi } from '../../api/projects'
import { useAvailablePlugins } from '../../api/plugins'
import { ProjectEditor } from '../projects/ProjectEditor'
import { Contacts } from '../projects/ContactFields'
import { ContextExtensions } from '../projects/ContextExtensions'
import { ProjectReportsPanel } from '../projects/ProjectReportsPanel'
import { WorkDigestPanel } from '../projects/WorkDigestPanel'
import { WorkSchedulesPanel } from '../projects/WorkSchedulesPanel'
import {ProjectWorkOverview} from '../projects/ProjectWorkOverview'
import {ProjectArtwork} from '../projects/ProjectArtwork'
import {ProjectRepositories} from '../projects/ProjectRepositories'
import {ProjectBoardLinks} from '../projects/ProjectBoardLinks'
import {ProjectDevelopment,ProjectRepositoryShortcuts} from '../projects/ProjectDevelopment'
import { Button } from '../ui'
import '../../styles/projects.css'

export default function ProjectDetailPage() {
  const {projectId = ''} = useParams(); const client = useQueryClient(); const [edit, setEdit] = useState(false); const [tab, setTab] = useState('overview')
  const plugins = useAvailablePlugins()
  const project = useQuery({queryKey: ['project', projectId], queryFn: () => projectsApi.get(projectId)})
  const members = useQuery({queryKey: ['project-members', projectId], queryFn: () => projectsApi.members(projectId)})
  const options = useQuery({queryKey: ['project-options'], queryFn: projectsApi.options})
  const risks = useQuery({queryKey: ['project-risks', projectId], queryFn: () => projectsApi.risks(projectId), enabled: project.data?.canManage === true && tab === 'risk'})
  if (project.isLoading) return <p role="status">Loading project…</p>
  if (project.isError || !project.data) return <div className="page-stack"><p role="alert">This project is unavailable.</p><Link to="/projects">Back to projects</Link></div>
  const value = project.data
  return <div className="page-stack project-details"><Link to="/projects">Back to projects</Link><div className="page-heading"><div><h1>{value.name}</h1><p>{value.clientId?<Link to={`/projects/clients/${value.clientId}`}>{value.clientName}</Link>:value.clientName} · {value.categoryName} · {value.details.tagIds.map(id => options.data?.tags.find(tag => tag.id === id)?.name).filter(Boolean).join(', ')}</p></div>{value.canManage && <Button disabled={edit} onClick={() => setEdit(true)}>Edit project</Button>}</div>
    <ProjectArtwork id={value.id} canManage={value.canManage}/>
    {edit ? <ProjectEditor id={value.id} initial={value} onCancel={() => setEdit(false)} onSave={saved => {client.setQueryData(['project', projectId], saved); void client.invalidateQueries({queryKey: ['projects']}); void client.invalidateQueries({queryKey: ['project-members', projectId]}); setEdit(false)}} /> : <>
      <nav className="inline-actions" aria-label="Project detail sections">{[['overview', 'Overview'], ['reports', 'Reports'], ...(value.canManage ? [['risk', 'Delivery risk'], ['digest', 'Team digests'], ['schedules', 'Schedules']] : [])].map(([key, label]) => <Button key={key} variant={tab === key ? 'secondary' : 'ghost'} onClick={() => setTab(key)}>{label}</Button>)}</nav>
      {tab === 'overview' && <><p className="project-note">{value.details.description}</p><ProjectBoardLinks projectId={value.id} canManage={value.canManage}/><ProjectRepositoryShortcuts projectId={value.id}/>
        {plugins.data?.some(plugin => plugin.manifest.id === 'kanban') && <ProjectWorkOverview projectId={value.id}/>}
        <ProjectRepositories projectId={value.id} canManage={value.canManage}/><ProjectDevelopment projectId={value.id} canManage={value.canManage}/>
        <section className="page-stack"><h2>People working on this project</h2>{members.data?.map(person => <div key={person.id}>{person.name} · {person.email}{person.id === value.leadId ? ' · Project lead' : ''}</div>)}</section>
        <section className="page-stack"><h2>{value.details.contactSource === 'CLIENT' && value.clientId ? `Client contacts · ${value.clientName}` : 'Project contacts'}</h2><Contacts contacts={value.effectiveContacts} /></section>
        <section className="page-stack"><h2>Documents & folders</h2>{value.details.storage.map(link => {const parent = link.path.includes('/') ? link.path.slice(0, link.path.lastIndexOf('/') + 1) : ''; return <Link key={link.id} to={`/storage?folder=${link.folderId}&path=${encodeURIComponent(link.directory ? link.path : parent)}${!link.directory ? '&file=' + encodeURIComponent(link.path) : ''}`}>{link.label} · {link.directory ? 'Folder' : 'File'}</Link>})}{!value.details.storage.length && <p>No accessible storage references.</p>}</section>
        <section className="page-stack"><h2>Web links</h2>{value.details.links.map((link, index) => <a href={link.url} target="_blank" rel="noreferrer" key={index}>{link.label}</a>)}</section><ContextExtensions kind="PROJECT" id={value.id} /></>}
      {tab === 'reports' && <ProjectReportsPanel project={value} />}{tab === 'digest' && value.canManage && <WorkDigestPanel projectId={value.id} />}{tab === 'schedules' && value.canManage && <WorkSchedulesPanel projectId={value.id} />}
      {tab === 'risk' && value.canManage && <section className="page-stack"><h2>Delivery risk by assignee</h2><p>Visible only to project managers, team leads, and administrators. Scores describe current delivery conditions, not employee performance.</p>{risks.isLoading && <p role="status">Calculating delivery indicators…</p>}{risks.isError && <p role="alert">Unable to load delivery indicators.</p>}<div className="project-table-wrap"><table className="project-table"><thead><tr><th>Member</th><th>Score / 100</th><th>Overdue</th><th>Blocked</th><th>In progress</th><th>Unstarted due soon</th></tr></thead><tbody>{risks.data?.map(risk => <tr key={risk.userId}><td>{risk.name}</td><td>{risk.score}</td><td>{risk.overdue}</td><td>{risk.blocked}</td><td>{risk.inProgress}</td><td>{risk.dueSoon}</td></tr>)}</tbody></table></div><p className="field-help">{risks.data?.[0]?.explanation}</p></section>}
    </>}
  </div>
}
