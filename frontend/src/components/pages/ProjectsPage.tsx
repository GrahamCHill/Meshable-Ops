import { useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { projectsApi, emptyDetails } from '../../api/projects'
import { useAuthStore } from '../../hooks/useAuthStore'
import { ProjectEditor } from '../projects/ProjectEditor'
import { ClientsPanel } from '../projects/ClientsPanel'
import { WorkDigestPanel } from '../projects/WorkDigestPanel'
import { WorkSchedulesPanel } from '../projects/WorkSchedulesPanel'
import { Button } from '../ui'
import {ProjectDirectoryFilters} from '../projects/ProjectDirectoryFilters'
import '../../styles/projects.css'

export default function ProjectsPage() {
  const [params] = useSearchParams(); const client = params.get('client'); const user = useAuthStore(state => state.user); const navigate = useNavigate()
  const [tab, setTab] = useState('projects'); const [create, setCreate] = useState(false)
  const options = useQuery({queryKey: ['project-options'], queryFn: projectsApi.options})
  return <div className="page-stack project-details"><div className="page-heading"><div><h1>Projects</h1><p>Clients, teams, delivery boards, and connected documents.</p></div>{options.data?.canCreateProjects&&<Button onClick={() => setCreate(true)} disabled={create}>New project</Button>}</div>
    <nav className="inline-actions" aria-label="Project sections">{[['projects', 'Projects'], ['clients', 'Clients'], ['digest', 'My work digest'], ['schedules', 'My schedules']].map(([key, label]) => <Button key={key} variant={tab === key ? 'secondary' : 'ghost'} disabled={create} onClick={() => setTab(key)}>{label}</Button>)}</nav>
    {create ? <ProjectEditor initial={{name: '', leadId: user?.id ?? '', clientId: client, category: options.data?.defaultSectionId ?? 'ACTIVE', details: emptyDetails(), revision: -1}} onCancel={() => setCreate(false)} onSave={project => navigate(`/projects/${project.id}`)} /> : tab === 'clients' ? <ClientsPanel canCreate={options.data?.canCreateClients===true} /> : tab === 'digest' ? <WorkDigestPanel /> : tab === 'schedules' ? <WorkSchedulesPanel /> : <>
      {options.isLoading&&<p role="status">Loading project categories…</p>}{options.isError&&<p role="alert">Project categories could not load.</p>}{options.data&&<ProjectDirectoryFilters key={user?.id} userId={user?.id??'anonymous'} options={options.data} initialClient={client}/>}

    </>}
  </div>
}
