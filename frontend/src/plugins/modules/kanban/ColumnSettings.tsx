import {Button} from '../../../components/ui'
import type {Person} from '../../../api/projects'
import type {BoardColumn, BoardCard, ColumnPermission, MoveMode} from './api'

const modes: [MoveMode,string][] = [['EDITORS','All board editors'],['ASSIGNEE','Ticket assignee'],['REVIEWERS','Ticket reviewers'],['SELECTED_MEMBERS','Selected members'],['MANAGERS','Board managers only']]
export function Permission({label,value,people,onChange}: {label:string;value?:ColumnPermission;people:Person[];onChange:(value:ColumnPermission)=>void}) {
  const rule=value??{mode:'EDITORS',userIds:[]}
  return <div className="page-stack"><label className="form-field">{label}<select value={rule.mode} onChange={e=>onChange({mode:e.target.value as MoveMode,userIds:[]})}>{modes.map(([id,text])=><option key={id} value={id}>{text}</option>)}</select></label>
    {rule.mode==='SELECTED_MEMBERS'&&<div className="project-choices">{people.map(person=><label key={person.id}><input type="checkbox" checked={rule.userIds.includes(person.id)} onChange={e=>onChange({...rule,userIds:e.target.checked?[...rule.userIds,person.id]:rule.userIds.filter(id=>id!==person.id)})}/>{person.name}</label>)}{!rule.userIds.length&&<small>No members selected: only board managers can move here.</small>}</div>}
  </div>
}

export function ColumnSettings({columns,cards,people,onChange}: {columns:BoardColumn[];cards:BoardCard[];people:Person[];onChange:(columns:BoardColumn[])=>void}) {
  const patch=(id:string,value:Partial<BoardColumn>)=>onChange(columns.map(column=>column.id===id?{...column,...value}:column))
  const reorder=(index:number,delta:number)=>{const next=[...columns];const [item]=next.splice(index,1);next.splice(index+delta,0,item);onChange(next)}
  return <div className="page-stack"><p>Columns run left to right. Both departure and destination permissions apply. Board managers retain access.</p>
    {columns.map((column,index)=><details key={column.id} className="kanban-config-column"><summary>{index+1}. {column.title}</summary><div className="page-stack">
      <label className="form-field">Column name<input required maxLength={80} value={column.title} onChange={e=>patch(column.id,{title:e.target.value})}/></label>
      <label className="form-field">Column purpose<select value={column.status??''} onChange={e=>patch(column.id,{status:e.target.value as BoardColumn['status']||null})}><option value="">Keep existing ticket status</option><option value="TODO">To do</option><option value="IN_PROGRESS">In progress</option><option value="DONE">Completed tickets (Done)</option></select></label>
      {column.status==='DONE'&&<p className="field-help">Completed column: moving a ticket here automatically marks it Done and records its completion date. Moving it back to an open column reopens it.</p>}
      <div className="project-fields"><Permission label="Who can move into this column" value={column.enter} people={people} onChange={enter=>patch(column.id,{enter})}/><Permission label="Who can move out of this column" value={column.leave} people={people} onChange={leave=>patch(column.id,{leave})}/></div>
      <div className="inline-actions"><Button type="button" variant="ghost" disabled={!index} onClick={()=>reorder(index,-1)}>Move left</Button><Button type="button" variant="ghost" disabled={index===columns.length-1} onClick={()=>reorder(index,1)}>Move right</Button><Button type="button" variant="ghost" disabled={columns.length===1||cards.some(card=>card.columnId===column.id)} onClick={()=>onChange(columns.filter(item=>item.id!==column.id))}>Remove empty column</Button></div>
    </div></details>)}
    <div><Button type="button" variant="secondary" disabled={columns.length>=20} onClick={()=>onChange([...columns,{id:crypto.randomUUID(),title:'New column',status:'IN_PROGRESS'}])}>Add column</Button></div>
  </div>
}
