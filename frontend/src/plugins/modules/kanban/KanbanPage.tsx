import {useState} from 'react'
import {Link,useParams,useSearchParams} from 'react-router-dom'
import {useMutation,useQuery,useQueryClient} from '@tanstack/react-query'
import {pluginRole} from '../../../api/access'
import {useAuthStore} from '../../../hooks/useAuthStore'
import {useAvailablePlugins} from '../../../api/plugins'
import {Button} from '../../../components/ui'
import {getErrorMessage} from '../../../components/admin/adminUtils'
import {boardsApi,blankBoard,type Board} from './api'
import {BoardEditor} from './BoardEditor'
import {BoardConfiguration} from './BoardConfiguration'
import './kanban.css'
import '../../../styles/projects.css'

export default function KanbanPage() {
  const {'*':section=''}=useParams()
  return section==='guide'?<div className="page-stack"><h1>Board guide</h1><p>Open a board, create tickets inline in a column, and drag them between columns or above/below other tickets. Use the move up/down controls for keyboard ordering, or open a ticket to change its column. Filter by multiple assignees, labels, epic, sprint, type, priority or flagged work, and group the board by assignee or epic.</p><p>Use Backlog to plan tickets into future sprints. Managers create and start sprints, then complete the active sprint with unfinished work moved to the backlog or a future sprint. Subtasks follow their parent's sprint. Expand a card's subtask footer to inspect children; open the parent to create a subtask. Complete all subtasks before moving their parent to Done.</p><p>Board managers configure column order, reporting status, and entry/exit permissions. Both columns must permit a move. Private owners and project managers/team leads manage their boards; plugin table permissions still apply. Changes save immediately, and concurrent changes ask you to reload.</p><p>Closing a dialog keeps its draft on this page. Leaving the page clears it. Project dashboards use only boards and tickets you may access.</p><Link to="/plugins/kanban/boards">Open boards</Link></div>:<Boards/>
}

function Boards() {
  const [params,setParams]=useSearchParams(),[create,setCreate]=useState(false),[query,setQuery]=useState('')
  const [fresh]=useState(blankBoard)
  const user=useAuthStore(state=>state.user),role=pluginRole(user?.role??'')
  const plugins=useAvailablePlugins(),client=useQueryClient()
  const canWrite=!!plugins.data?.find(item=>item.manifest.id==='kanban')?.tableAccess.plugin_kanban_boards?.writeRoles.includes(role)
  const selected=params.get('board')??'',projectId=params.get('project')??undefined
  const select=(id:string)=>setParams({...projectId?{project:projectId}:{},...id?{board:id}:{}})
  const list=useQuery({queryKey:['plugin-kanban-boards',user?.id,role,projectId],queryFn:()=>boardsApi.list(projectId)})
  const board=useQuery({queryKey:['plugin-kanban-board',user?.id,role,selected],queryFn:()=>boardsApi.get(selected),enabled:!!selected,refetchOnWindowFocus:false})
  const saved=(value:Board)=>{
    client.setQueryData(['plugin-kanban-board',user?.id,role,value.id],value)
    void client.invalidateQueries({queryKey:['plugin-kanban-boards']})
    void client.invalidateQueries({queryKey:['project-work-summary']})
    if(selected!==value.id)select(value.id)
  }
  const creation=useMutation({mutationFn:(input:Parameters<typeof boardsApi.save>[1])=>boardsApi.save(undefined,input),onSuccess:value=>{setCreate(false);saved(value)}})
  if(selected)return <div className="page-stack"><div className="inline-actions kanban-board-navigation"><button className="kanban-ticket-link" onClick={()=>select('')}>All boards</button>{projectId&&<Link to={'/projects/'+projectId}>Back to project</Link>}<Button variant="ghost" onClick={()=>void board.refetch()}>Reload board</Button></div>
    {board.isLoading?<p role="status">Loading board…</p>:board.isError||!board.data?<p role="alert">This board is unavailable. Reload or return to all boards.</p>:<BoardEditor key={board.data.id} initial={board.data} onSaved={saved} readOnly={!canWrite}/>}
  </div>
  const items=list.data?.filter(item=>item.name.toLowerCase().includes(query.toLowerCase()))
  return <div className="page-stack">
    <div className="page-heading"><div><h1>Boards</h1><p>Organize work, plan sprints, and follow delivery.</p></div><Button disabled={!canWrite} onClick={()=>{creation.reset();setCreate(true)}}>New board</Button></div>
    {projectId&&<Link to={'/projects/'+projectId}>Back to project</Link>}
    <label className="form-field kanban-board-search">Find a board<input type="search" placeholder="Search boards" value={query} onChange={e=>setQuery(e.target.value)}/></label>
    {list.isLoading&&<p role="status">Loading boards…</p>}{list.isError&&<p role="alert">Unable to load boards. <button onClick={()=>void list.refetch()}>Retry</button></p>}
    <div className="kanban-board-grid">{items?.map(item=><button key={item.id} type="button" className="kanban-board-card" onClick={()=>select(item.id)}>
      <span className="kanban-board-card-kind">{item.projectId?'Project board':'Private board'}</span><h2>{item.name}</h2><span>{item.ticketCount??0} tickets · {item.completedCount??0} completed</span>
      <span className="kanban-board-progress" role="img" aria-label={(item.completedCount??0)+' of '+(item.ticketCount??0)+' tickets completed'}><i style={{width:((item.ticketCount?(item.completedCount??0)/item.ticketCount:0)*100)+'%'}}/></span>
      <small>Updated {new Date(item.updatedAt).toLocaleDateString()}</small><span className="kanban-board-card-open">Open board →</span>
    </button>)}</div>
    {!list.isLoading&&!list.isError&&!items?.length&&<p>{query?'No boards match your search.':'Create a board to start organizing work.'}</p>}
    {create&&<BoardConfiguration board={{...fresh,projectId:projectId??null}} people={user?[{id:user.id,name:user.name??user.username,email:user.email}]:[]} busy={creation.isPending} error={creation.isError?getErrorMessage(creation.error):undefined} onSave={input=>creation.mutate(input)} onClose={()=>setCreate(false)}/>}
  </div>
}
