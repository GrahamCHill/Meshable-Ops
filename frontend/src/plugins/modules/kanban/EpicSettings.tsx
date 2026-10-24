import {Button} from '../../../components/ui'
import type {BoardEpic,BoardCard} from './api'

export function EpicSettings({epics,cards,onChange}: {epics:BoardEpic[];cards:BoardCard[];onChange:(value:BoardEpic[])=>void}) {
  const patch=(id:string,value:Partial<BoardEpic>)=>onChange(epics.map(epic=>epic.id===id?{...epic,...value}:epic))
  return <div className="page-stack"><p>Group related tickets under an epic. Remove ticket links before deleting an epic.</p>{epics.map(epic=><section className="page-stack kanban-config-column" key={epic.id}>
    <div className="project-fields"><label className="form-field">Epic name<input required maxLength={100} value={epic.name} onChange={e=>patch(epic.id,{name:e.target.value})}/></label><label className="form-field">Epic color<input type="color" value={epic.color} onChange={e=>patch(epic.id,{color:e.target.value})}/></label></div>
    <label className="form-field">Epic description<textarea maxLength={2000} value={epic.description} onChange={e=>patch(epic.id,{description:e.target.value})}/></label>
    <div><Button type="button" variant="ghost" disabled={cards.some(card=>card.epicId===epic.id)} onClick={()=>onChange(epics.filter(item=>item.id!==epic.id))}>Remove epic</Button></div>
  </section>)}<div><Button type="button" variant="secondary" disabled={epics.length>=100} onClick={()=>onChange([...epics,{id:crypto.randomUUID(),name:'New epic',description:'',color:'#6554c0'}])}>Add epic</Button></div></div>
}
