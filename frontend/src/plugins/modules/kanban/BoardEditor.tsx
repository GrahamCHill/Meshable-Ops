import {useEffect,useState} from 'react'
import {useMutation,useQuery} from '@tanstack/react-query'
import {Link} from 'react-router-dom'
import {Button} from '../../../components/ui'
import {getErrorMessage} from '../../../components/admin/adminUtils'
import {usePageDraft,useDiscardPageDraft} from '../../../hooks/usePageDraft'
import {boardsApi,type Board,type BoardCard,type BoardInput} from './api'
import {CardEditor} from './CardEditor'
import {BoardConfiguration} from './BoardConfiguration'
import {DevelopmentLinks} from './DevelopmentLinks'
import {TicketTile} from './TicketTile'
import {WorkCharts} from '../../../components/projects/WorkCharts'
import {boardSummary} from './boardSummary'
import '../../../styles/projects.css'

export function BoardEditor({initial,onSaved,readOnly=false}: {initial:Board;onSaved:(board:Board)=>void;readOnly?:boolean}) {
  const [draft,setDraft]=useState(initial)
  const [editing,setEditing]=useState<BoardCard>()
  const [configure,setConfigure]=useState(false)
  const [tab,setTab]=useState('board')
  const [query,setQuery]=useState(''),[assignee,setAssignee]=useState(''),[epic,setEpic]=useState(''),[tag,setTag]=useState(''),[sprint,setSprint]=useState('')
  const [drag,setDrag]=useState<{kind:'ticket'|'column';id:string}>()
  const [fresh,setFresh]=usePageDraft<Record<string,BoardCard>>('new-tickets:'+initial.id,{})
  const discard=useDiscardPageDraft()
  const members=useQuery({queryKey:['board-assignees',initial.id],queryFn:()=>boardsApi.members(initial.id)})
  const accept=(board:Board)=>{setDraft(board);onSaved(board)}
  const save=useMutation({mutationFn:(board:BoardInput)=>boardsApi.save(initial.id,board),onSuccess:accept})
  const move=useMutation({mutationFn:({card,column,before}:{card:string;column:string;before?:string})=>boardsApi.move(initial.id,card,column,draft.revision,before),onSuccess:accept})
  useEffect(()=>setDraft(initial),[initial])
  const busy=save.isPending||move.isPending
  const error=save.isError?getErrorMessage(save.error):move.isError?getErrorMessage(move.error):undefined
  const canManage=draft.canManage&&!readOnly
  const allowed=(card:string,column:string)=>!readOnly&&!busy&&draft.moves[card]?.includes(column)
  const doMove=(card:string,column:string,before?:string)=>{if(!allowed(card,column)||card===before)return;move.mutate({card,column,before});setDrag(undefined)}
  const add=(columnId:string)=>{
    if(readOnly||busy)return
    const card=fresh[columnId]??{id:crypto.randomUUID(),columnId,title:'',description:'',status:draft.content.columns.find(column=>column.id===columnId)?.status??'TODO',reviewerIds:[],tags:[],type:'TASK',priority:'MEDIUM'}
    setFresh({...fresh,[columnId]:card});setEditing(card);save.reset()
  }
  const saveCard=(card:BoardCard)=>save.mutate({...draft,content:{...draft.content,cards:draft.content.cards.some(item=>item.id===card.id)?draft.content.cards.map(item=>item.id===card.id?card:item):[...draft.content.cards,card]}},{onSuccess:()=>{
    discard('ticket:'+draft.id+':'+card.id);discard('ticket:'+draft.id+':'+card.id+':tags')
    setFresh(Object.fromEntries(Object.entries(fresh).filter(([,item])=>item.id!==card.id)));setEditing(undefined)
  }})
  const cards=draft.content.cards.filter(card=>(!query||[card.title,card.reference,card.description,...card.tags??[]].join(' ').toLowerCase().includes(query.toLowerCase()))
    &&(!assignee||(assignee==='unassigned'?!card.assigneeId:card.assigneeId===assignee))&&(!epic||(epic==='none'?!card.epicId:card.epicId===epic))
    &&(!tag||card.tags?.includes(tag))&&(!sprint||(sprint==='backlog'?!card.sprintId:card.sprintId===sprint)))
  const visible=tab==='backlog'?cards.filter(card=>!card.sprintId):cards
  const clearFilters=()=>{setQuery('');setAssignee('');setEpic('');setTag('');setSprint('')}
  const dropColumn=(target:string)=>{
    if(drag?.kind!=='column'||!canManage||busy||drag.id===target)return
    const columns=[...draft.content.columns],from=columns.findIndex(column=>column.id===drag.id),to=columns.findIndex(column=>column.id===target)
    const [column]=columns.splice(from,1);columns.splice(to,0,column);save.mutate({...draft,content:{...draft.content,columns}});setDrag(undefined)
  }
  return <div className="page-stack kanban-workspace">
    <div className="page-heading"><div><h1>{draft.name}</h1>{draft.projectId&&<Link to={'/projects/'+draft.projectId}>Open project</Link>}</div><div className="inline-actions">{canManage&&<Button variant="ghost" disabled={busy} onClick={()=>{save.reset();setConfigure(true)}}>Configure board</Button>}<Button disabled={readOnly||busy||draft.content.cards.length>=200} onClick={()=>add(draft.content.columns[0].id)}>Create ticket</Button></div></div>
    <nav className="workspace-tabs" aria-label="Board views">{[['board','Board'],['backlog','Backlog'],['list','Tickets'],['summary','Summary']].map(([key,label])=><button key={key} aria-current={tab===key?'page':undefined} onClick={()=>setTab(key)}>{label}</button>)}</nav>
    {readOnly&&<p className="field-help">Read-only board. You can inspect tickets and comments.</p>}
    {tab!=='summary'&&<div className="kanban-filters">
      <input type="search" aria-label="Search board" placeholder="Search board" value={query} onChange={e=>setQuery(e.target.value)}/>
      <select aria-label="Filter assignee" value={assignee} onChange={e=>setAssignee(e.target.value)}><option value="">All assignees</option><option value="unassigned">Unassigned</option>{members.data?.map(person=><option key={person.id} value={person.id}>{person.name}</option>)}</select>
      <select aria-label="Filter epic" value={epic} onChange={e=>setEpic(e.target.value)}><option value="">All epics</option><option value="none">No epic</option>{draft.content.epics?.map(item=><option key={item.id} value={item.id}>{item.name}</option>)}</select>
      <select aria-label="Filter tag" value={tag} onChange={e=>setTag(e.target.value)}><option value="">All tags</option>{[...new Set(draft.content.cards.flatMap(card=>card.tags??[]))].sort().map(value=><option key={value}>{value}</option>)}</select>
      <select aria-label="Filter sprint" value={sprint} onChange={e=>setSprint(e.target.value)}><option value="">All sprints</option><option value="backlog">Backlog</option>{draft.content.sprints?.map(item=><option key={item.id} value={item.id}>{item.name}</option>)}</select>
      {(query||assignee||epic||tag||sprint)&&<Button variant="ghost" onClick={clearFilters}>Clear filters</Button>}
      <span role="status">{visible.length} tickets{busy?' · Saving…':''}</span>
    </div>}
    {error&&!editing&&!configure&&<p role="alert">{error} Reload the board if it changed elsewhere.</p>}
    {tab==='summary'?<WorkCharts value={boardSummary(draft,members.data??[])}/>:tab==='board'?<>
      <p className="kanban-sr-only">Drag tickets between columns or open a ticket and choose its Column. Managers can drag column headings to reorder columns.</p>
      <div className="kanban-columns">
        {draft.content.columns.map(column=>{
          const canDrop=drag?.kind==='column'?canManage:drag?.kind==='ticket'&&allowed(drag.id,column.id)
          return <section key={column.id} className={'kanban-column'+(canDrop?' can-drop':'')} aria-label={column.title}
            onDragOver={e=>{if(canDrop){e.preventDefault();e.dataTransfer.dropEffect='move'}}}
            onDrop={e=>{e.preventDefault();if(drag?.kind==='column')dropColumn(column.id);else if(drag?.kind==='ticket')doMove(drag.id,column.id)}}>
            <header className="kanban-column-heading" draggable={canManage&&!busy} onDragStart={e=>{e.dataTransfer.setData('text/plain',column.id);e.dataTransfer.effectAllowed='move';setDrag({kind:'column',id:column.id})}} onDragEnd={()=>setDrag(undefined)}>
              <h2>{column.title}</h2><span aria-label="Ticket count">{cards.filter(card=>card.columnId===column.id).length}</span>
            </header>
            <div className="kanban-column-tickets">{cards.filter(card=>card.columnId===column.id).map(card=><TicketTile key={card.id} card={card} epic={draft.content.epics?.find(item=>item.id===card.epicId)} person={members.data?.find(person=>person.id===card.assigneeId)} subtasks={draft.content.cards.filter(item=>item.parentId===card.id)} disabled={busy}
              draggable={!readOnly&&!busy&&!!draft.moves[card.id]?.length} onOpen={()=>{save.reset();setEditing(card)}}
              onDragStart={e=>{e.stopPropagation();e.dataTransfer.setData('text/plain',card.id);e.dataTransfer.effectAllowed='move';setDrag({kind:'ticket',id:card.id})}} onDragEnd={()=>setDrag(undefined)}
              onDragOver={e=>{if(drag?.kind==='ticket'&&allowed(drag.id,column.id)){e.preventDefault();e.stopPropagation()}}}
              onDrop={e=>{if(drag?.kind==='ticket'){e.preventDefault();e.stopPropagation();doMove(drag.id,column.id,card.id)}}}
            />)}
            {!readOnly&&<button type="button" className="kanban-add-ticket" disabled={busy||draft.content.cards.length>=200} onClick={()=>add(column.id)}>+ Create ticket</button>}
            </div>
          </section>
        })}
        {canManage&&<button className="kanban-add-column" disabled={busy} onClick={()=>{save.reset();setConfigure(true)}}>+ Add column</button>}
      </div>
    </>:<div className="project-table-wrap"><table className="project-table"><thead><tr><th>Ticket</th><th>Column</th><th>Assignee</th><th>Epic</th><th>Tags</th><th>Due</th></tr></thead><tbody>{visible.map(card=><tr key={card.id}><td><button className="kanban-ticket-link" onClick={()=>{save.reset();setEditing(card)}}>{card.reference} {card.title}</button></td><td>{draft.content.columns.find(column=>column.id===card.columnId)?.title}</td><td>{members.data?.find(person=>person.id===card.assigneeId)?.name??'Unassigned'}</td><td>{draft.content.epics?.find(item=>item.id===card.epicId)?.name??'–'}</td><td>{card.tags?.join(', ')}</td><td>{card.dueDate??'–'}</td></tr>)}</tbody></table>{!visible.length&&<p>No tickets match this view.</p>}</div>}
    {configure&&canManage&&<BoardConfiguration id={draft.id} board={draft} people={members.data??[]} busy={busy} error={error} onSave={value=>save.mutate(value,{onSuccess:()=>{discard('board-config:'+draft.id);setConfigure(false)}})} onClose={()=>setConfigure(false)}/>}
    {editing&&<CardEditor key={editing.id} boardId={draft.id} card={editing} cards={draft.content.cards} columns={draft.content.columns} epics={draft.content.epics} sprints={draft.content.sprints} people={members.data??[]} allowedColumns={draft.moves[editing.id]} busy={busy} readOnly={readOnly} error={error} onCancel={()=>setEditing(undefined)} onSave={saveCard}
      onRemove={draft.content.cards.some(card=>card.id===editing.id)?()=>save.mutate({...draft,content:{...draft.content,cards:draft.content.cards.filter(card=>card.id!==editing.id)}},{onSuccess:()=>{discard('ticket:'+draft.id+':'+editing.id);setEditing(undefined)}}):undefined}/>}
    <details><summary>Repositories and pull requests</summary><DevelopmentLinks boardId={draft.id} cards={draft.content.cards} locked={readOnly||busy}/></details>
  </div>
}
