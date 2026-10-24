import {useState} from 'react'
import {useInfiniteQuery,useMutation,useQueryClient} from '@tanstack/react-query'
import {ChevronDown,ChevronRight} from 'lucide-react'
import {projectsApi,type ProjectSection,type ProjectSummary,type ProjectTag} from '../../api/projects'
import {useAuthStore} from '../../hooks/useAuthStore'
import {ProjectCards} from './ProjectCards'
import {Button} from '../ui'
import {getErrorMessage} from '../admin/adminUtils'

export function ProjectDirectory({sections,tags,category,query,clientId,filterTags,collapsed,onCollapse}:{sections:ProjectSection[];tags:ProjectTag[];category:string;query:string;clientId:string;filterTags:string[];collapsed:string[];onCollapse:(id:string)=>void}) {
  const client=useQueryClient(),[drag,setDrag]=useState<ProjectSummary>(),[over,setOver]=useState('')
  const move=useMutation({mutationFn:({project,section}:{project:ProjectSummary;section:string})=>projectsApi.moveSection(project,section),onSuccess:()=>{void client.invalidateQueries({queryKey:['project-directory']});void client.invalidateQueries({queryKey:['projects']});void client.invalidateQueries({queryKey:['project']})}})
  const doMove=(project:ProjectSummary,section:string)=>{if(project.canMove&&section!==project.category&&!move.isPending)move.mutate({project,section});setDrag(undefined);setOver('')}
  return <div className="page-stack project-sections">{move.isError&&<p role="alert">{getErrorMessage(move.error)} <button type="button" onClick={()=>void client.invalidateQueries({queryKey:['project-directory']})}>Reload projects</button></p>}{sections.filter(section=>!category||category===section.id).map(section=><section key={section.id} className={'page-stack project-directory-group'+(over===section.id?' is-drop-target':'')} onDragEnter={event=>{if(drag?.canMove){event.preventDefault();setOver(section.id)}}} onDragOver={event=>{if(drag?.canMove&&!move.isPending)event.preventDefault()}} onDrop={event=>{event.preventDefault();if(drag)doMove(drag,section.id)}}>
    <button className="project-section-toggle" type="button" aria-expanded={!collapsed.includes(section.id)} onClick={()=>onCollapse(section.id)}>{collapsed.includes(section.id)?<ChevronRight size={18}/>:<ChevronDown size={18}/>}<span>{section.name}</span>{over===section.id&&<small>Move project here</small>}</button>
    <DirectoryGroup section={section} sections={sections} tags={tags} query={query} clientId={clientId} filterTags={filterTags} collapsed={collapsed.includes(section.id)} busy={move.isPending} onMove={doMove} onDrag={setDrag}/>
  </section>)}</div>
}
function DirectoryGroup({section,sections,tags,query,clientId,filterTags,collapsed,busy,onMove,onDrag}:{section:ProjectSection;sections:ProjectSection[];tags:ProjectTag[];query:string;clientId:string;filterTags:string[];collapsed:boolean;busy:boolean;onMove:(project:ProjectSummary,section:string)=>void;onDrag:(project:ProjectSummary|undefined)=>void}) {
  const user=useAuthStore(state=>state.user?.id)
  const page=useInfiniteQuery({queryKey:['project-directory',user,section.id,query,clientId,filterTags],initialPageParam:0,enabled:!collapsed,queryFn:({pageParam})=>projectsApi.page(section.id,query,clientId,filterTags,pageParam),getNextPageParam:last=>last.hasMore?last.nextOffset:undefined})
  if(collapsed)return null
  const projects=[...new Map(page.data?.pages.flatMap(item=>item.items).map(item=>[item.id,item])??[]).values()]
  return <>{page.isLoading&&<p role="status">Loading {section.name} projects…</p>}{page.isError&&<p role="alert">Unable to load this category. <button type="button" onClick={()=>void page.refetch()}>Retry</button></p>}<ProjectCards projects={projects} tags={tags} sections={sections} busy={busy} onMove={onMove} onDrag={onDrag}/>{page.hasNextPage&&<div><Button variant="secondary" loading={page.isFetchingNextPage} onClick={()=>void page.fetchNextPage()}>Load up to 10 more projects</Button></div>}{page.isSuccess&&!projects.length&&<p className="field-help">No projects match these filters.</p>}<small>{projects.length}{page.hasNextPage?'+':''} projects loaded</small></>
}
