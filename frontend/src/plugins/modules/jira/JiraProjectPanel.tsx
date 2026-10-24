import {useState} from 'react'
import {useMutation,useQuery,useQueryClient} from '@tanstack/react-query'
import {ExternalLink,RefreshCw} from 'lucide-react'
import {jiraApi,type JiraProjectLink} from './api'
import {Button} from '../../../components/ui'
import {getErrorMessage} from '../../../components/admin/adminUtils'
import {useAuthStore} from '../../../hooks/useAuthStore'
import {ProjectPluginScope} from '../../ProjectPluginFrame'

export function JiraProjectPanel({projectId,canManage}: {projectId:string;canManage:boolean}) {
  const user=useAuthStore(state=>state.user),client=useQueryClient(),queryKey=['jira-project-links',user?.id,projectId]
  const links=useQuery({queryKey,queryFn:()=>jiraApi.list(projectId)})
  const [key,setKey]=useState(''),[label,setLabel]=useState('')
  const update=useMutation({mutationFn:({kind,link}:{kind:'sync'|'remove';link:JiraProjectLink})=>kind==='sync'?jiraApi.sync(projectId,link):jiraApi.remove(projectId,link),onSuccess:()=>client.invalidateQueries({queryKey})})
  const add=useMutation({mutationFn:()=>jiraApi.link(projectId,key,label),onSuccess:()=>{setKey('');setLabel('');void client.invalidateQueries({queryKey})}})
  return <section className="page-stack project-plugin-panel"><p className="field-help">Jira stays the source of work items. These snapshots reflect projects approved by your administrator and do not create Kanban tickets.</p>
    {links.isLoading&&<p role="status">Loading Jira projects…</p>}{links.isError&&<p role="alert">Unable to load Jira links. <button type="button" onClick={()=>void links.refetch()}>Retry</button></p>}
    {links.data?.map(link=><article className="project-plugin-card page-stack" key={link.id}><div className="page-heading"><div><h3>{link.label} <small>{link.key}</small></h3><div className="inline-actions">{link.boardUrl&&<a href={link.boardUrl} target="_blank" rel="noopener noreferrer">Open Jira board <ExternalLink size={13}/></a>}{link.url&&<a href={link.url} target="_blank" rel="noopener noreferrer">Open Jira project <ExternalLink size={13}/></a>}</div></div>{canManage&&<div className="inline-actions"><Button type="button" size="sm" variant="secondary" disabled={update.isPending} onClick={()=>update.mutate({kind:'sync',link})}><RefreshCw size={13}/>Refresh metrics</Button><Button type="button" size="sm" variant="ghost" disabled={update.isPending} onClick={()=>update.mutate({kind:'remove',link})}>Unlink</Button></div>}</div>
      {canManage&&<JiraBoardSelector projectId={projectId} link={link}/>}
      {link.metrics?<><div className="project-plugin-metrics">{[['Total',link.metrics.total],['Done',link.metrics.completed],['In progress',link.metrics.inProgress],['To do',link.metrics.todo],['Overdue',link.metrics.overdue],['Completed in 30 days',link.metrics.completedLast30Days],...(link.metrics.storyPoints!==null?[['Story points',link.metrics.storyPoints]]:[])].map(([title,count])=><div key={title}><strong>{count}</strong><span>{title}</span></div>)}</div><div className="project-plugin-breakdowns">{[['Statuses',link.metrics.statuses],['Issue types',link.metrics.types]].map(([title,counts])=><div key={String(title)}><h4>{String(title)}</h4>{Object.entries(counts).map(([name,count])=><div className="project-plugin-breakdown" key={name}><span>{name}</span><strong>{count}</strong></div>)}</div>)}</div><small className="field-help">Refreshed {new Date(link.metrics.syncedAt).toLocaleString()}{link.metrics.partial?' · Partial snapshot: the refresh page limit was reached.':''}</small></>:<p className="field-help">Refresh metrics to load the first snapshot.</p>}
    </article>)}
    {update.isError&&<p role="alert">{getErrorMessage(update.error)}</p>}
    {canManage&&<form className="project-plugin-add" onSubmit={event=>{event.preventDefault();add.mutate()}}><label className="form-field">Jira project key<input required maxLength={40} value={key} placeholder="OPS" onChange={event=>setKey(event.target.value.toUpperCase())}/></label><label className="form-field">Display name (optional)<input maxLength={120} value={label} onChange={event=>setLabel(event.target.value)}/></label><Button type="submit" loading={add.isPending} disabled={!key.trim()}>Link Jira project</Button>{add.isError&&<p role="alert">{getErrorMessage(add.error)}</p>}</form>}
    {!canManage&&!links.data?.length&&<p>No Jira projects are linked.</p>}
  </section>
}
function JiraBoardSelector({projectId,link}:{projectId:string;link:JiraProjectLink}) {
  const client=useQueryClient(),[open,setOpen]=useState(false),[selected,setSelected]=useState(String(link.boardId??''))
  const boards=useQuery({queryKey:['jira-board-options',projectId,link.id],queryFn:()=>jiraApi.boards(projectId,link),enabled:open,retry:false})
  const save=useMutation({mutationFn:()=>jiraApi.setBoard(projectId,link,selected?Number(selected):null),onSuccess:()=>{setOpen(false);void client.invalidateQueries({queryKey:['jira-project-links']})}})
  return <div className="page-stack"><Button type="button" size="sm" variant="ghost" onClick={()=>{setSelected(String(link.boardId??''));setOpen(value=>!value)}}>{open?'Cancel board selection':link.boardId?'Change linked board':'Choose Jira board'}</Button>{open&&<><label className="form-field">Jira board<select value={selected} disabled={boards.isLoading||save.isPending} onChange={event=>setSelected(event.target.value)}><option value="">No board shortcut</option>{boards.data?.map(board=><option key={board.id} value={board.id}>{board.name} · {board.type}</option>)}</select></label><p className="field-help">Shows up to 250 boards for this approved Jira project. The Jira token needs permission to read boards.</p><div><Button type="button" size="sm" disabled={boards.isLoading||boards.isError} loading={save.isPending} onClick={()=>save.mutate()}>Save board shortcut</Button></div>{boards.isError&&<p role="alert">{getErrorMessage(boards.error)} <button type="button" onClick={()=>void boards.refetch()}>Retry</button></p>}{save.isError&&<p role="alert">{getErrorMessage(save.error)}</p>}</>}</div>
}
export default function JiraProjectExtension({context}: {context:Record<string,unknown>}) {return typeof context.id==='string'?<ProjectPluginScope id={context.id}>{(id,canManage)=><JiraProjectPanel projectId={id} canManage={canManage}/>}</ProjectPluginScope>:null}
