import type {ReactNode} from 'react'
import {Link,useSearchParams} from 'react-router-dom'
import {useQuery} from '@tanstack/react-query'
import {projectsApi} from '../api/projects'
import {useAuthStore} from '../hooks/useAuthStore'
import './project-plugins.css'

export function ProjectPluginScope({id,children}: {id:string;children:(id:string,canManage:boolean)=>ReactNode}) {
  const user=useAuthStore(state=>state.user)
  const project=useQuery({queryKey:['project-plugin-scope',user?.id,id],queryFn:()=>projectsApi.get(id)})
  if(project.isLoading)return <p role="status">Loading project…</p>
  if(project.isError||!project.data)return <p role="alert">This project is unavailable.</p>
  return <>{children(id,project.data.canManage)}</>
}
export function ProjectPluginFrame({title,description,children}: {title:string;description:string;children:(id:string,canManage:boolean)=>ReactNode}) {
  const [params,setParams]=useSearchParams(),selected=params.get('project')??'',user=useAuthStore(state=>state.user)
  const projects=useQuery({queryKey:['plugin-project-options',user?.id],queryFn:()=>projectsApi.list(),enabled:!selected})
  return <div className="page-stack project-plugin-workspace"><div className="page-heading"><div><h1>{title}</h1><p>{description}</p></div>{selected&&<Link to={'/projects/'+selected}>Open project</Link>}</div>
    {selected?<><button type="button" className="kanban-ticket-link" onClick={()=>setParams({})}>Choose another project</button><ProjectPluginScope id={selected}>{children}</ProjectPluginScope></>:
      <><label className="form-field">Project<select value="" onChange={event=>setParams({project:event.target.value})}><option value="">Choose a project</option>{projects.data?.map(project=><option key={project.id} value={project.id}>{project.name}</option>)}</select></label>{projects.isError&&<p role="alert">Unable to load projects.</p>}{projects.data?.length===0&&<p>Create a project to use this extension.</p>}</>}
  </div>
}
