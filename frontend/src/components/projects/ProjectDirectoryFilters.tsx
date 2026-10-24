import {useEffect,useState} from 'react'
import {useQuery} from '@tanstack/react-query'
import {useSearchParams} from 'react-router-dom'
import {projectsApi,type ProjectOptions} from '../../api/projects'
import {ProjectDirectory} from './ProjectDirectory'
import {readProjectLayout,writeProjectLayout} from './projectLayout'

export function ProjectDirectoryFilters({userId,options,initialClient}:{userId:string;options:ProjectOptions;initialClient:string|null}) {
  const [,setParams]=useSearchParams(),[query,setQuery]=useState('')
  const [layout,setLayout]=useState(()=>{const value=readProjectLayout(document.cookie,userId,options.inactiveSectionId??'ARCHIVED');return {...value,clientId:initialClient??value.clientId}})
  const clients=useQuery({queryKey:['project-clients',userId],queryFn:projectsApi.clients})
  useEffect(()=>writeProjectLayout(userId,layout),[userId,layout])
  useEffect(()=>{if(initialClient!==null)setLayout(value=>({...value,clientId:initialClient}))},[initialClient])
  const category=options.sections.some(section=>section.id===layout.category)?layout.category:''
  return <><div className="project-fields"><label className="form-field">Find projects<input type="search" maxLength={200} value={query} onChange={event=>setQuery(event.target.value)}/></label><label className="form-field">Client<select value={layout.clientId} onChange={event=>{const clientId=event.target.value;setLayout({...layout,clientId});setParams(clientId?{client:clientId}:{})}}><option value="">All clients</option>{clients.data?.map(client=><option key={client.id} value={client.id}>{client.name}</option>)}</select></label><label className="form-field">Category<select value={category} onChange={event=>setLayout({...layout,category:event.target.value})}><option value="">All categories</option>{options.sections.map(section=><option key={section.id} value={section.id}>{section.name}</option>)}</select></label></div>
    {clients.isError&&<p role="alert">Client filters could not load.</p>}<div className="project-choices" aria-label="Filter by tags">{options.tags.map(tag=><label key={tag.id}><input type="checkbox" checked={layout.tags.includes(tag.id)} onChange={event=>setLayout({...layout,tags:event.target.checked?[...layout.tags,tag.id]:layout.tags.filter(id=>id!==tag.id)})}/>{tag.name}</label>)}</div>
    <p className="field-help">Expand a category to load projects, ten at a time. Authorised users can drag a card to another category or use its Move project control. The inactive category starts collapsed on each visit.</p>
    <ProjectDirectory sections={options.sections} tags={options.tags} category={category} query={query} clientId={layout.clientId} filterTags={layout.tags} collapsed={layout.collapsed} onCollapse={id=>setLayout({...layout,collapsed:layout.collapsed.includes(id)?layout.collapsed.filter(item=>item!==id):[...layout.collapsed,id]})}/>
  </>
}
